package com.typenull.pingdom.place.application.service.registration;

import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationAttachmentResponse;
import com.typenull.pingdom.place.domain.exception.PlaceRegistrationErrorCode;
import com.typenull.pingdom.place.domain.exception.PlaceRegistrationException;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachmentType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationAttachmentRepository;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.MerchantPlaceAttachmentMalwareScanner;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 통합 Merchant 장소 신청 첨부를 서버가 직접 검증·저장하는 경계입니다.
 * 외부 요청의 storageKey, 해시, 크기 등의 메타데이터는 사용하지 않습니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantPlaceApplicationAttachmentService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final int SENSITIVE_RETENTION_DAYS = 30;
    private static final String OUTBOX_AGGREGATE_TYPE = "MERCHANT_PLACE_APPLICATION_ATTACHMENT";

    private final PlaceRegistrationApplicationRepository applicationRepository;
    private final PlaceRegistrationAttachmentRepository attachmentRepository;
    private final S3ObjectStorage storage;
    private final S3ObjectDeleteOutboxPublisher deletePublisher;
    private final MerchantPlaceAttachmentMalwareScanner malwareScanner;
    private final Clock clock;

    @org.springframework.transaction.annotation.Transactional
    public MerchantPlaceApplicationAttachmentResponse upload(
            Long userId,
            Long applicationId,
            PlaceRegistrationAttachmentType documentType,
            MultipartFile file
    ) {
        PlaceRegistrationApplication application = ownedDraft(userId, applicationId);
        byte[] content = validateAndRead(file, documentType);
        malwareScanner.scan(content);
        String contentType = file.getContentType();
        String hash = sha256(content);
        List<PlaceRegistrationAttachment> existing = attachmentRepository
                .findAllByApplicationIdAndDocumentTypeOrderByDisplayOrderAscIdAsc(applicationId, documentType);
        if (existing.stream().anyMatch(attachment -> attachment.getFileHash().equals(hash))) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.DUPLICATE_ATTACHMENT);
        }

        if (isSensitive(documentType) && !existing.isEmpty()) {
            PlaceRegistrationAttachment previous = existing.getFirst();
            attachmentRepository.delete(previous);
            attachmentRepository.flush();
            deletePublisher.publish(previous.getStorageKey(), OUTBOX_AGGREGATE_TYPE, applicationId.toString(), "REPLACED");
        }

        S3ObjectStorage.S3PutResult uploaded = storage.putPrivate(
                content,
                contentType,
                "private/merchant-place-applications/" + applicationId + "/" + documentType.name().toLowerCase()
        );
        try {
            LocalDateTime now = now();
            PlaceRegistrationAttachment saved = attachmentRepository.saveAndFlush(PlaceRegistrationAttachment.create(
                    application,
                    null,
                    documentType,
                    uploaded.key(),
                    safeFilename(file.getOriginalFilename()),
                    contentType,
                    content.length,
                    hash,
                    userId,
                    now,
                    retentionExpiresAt(documentType, now),
                    isSensitive(documentType) ? 0 : existing.size()
            ));
            deleteUploadedObjectOnRollback(uploaded.key());
            return MerchantPlaceApplicationAttachmentResponse.from(saved);
        } catch (RuntimeException exception) {
            deleteUploadedObject(uploaded.key());
            throw exception;
        }
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<MerchantPlaceApplicationAttachmentResponse> list(Long userId, Long applicationId) {
        owned(userId, applicationId);
        return attachmentRepository.findAllByApplicationIdOrderByDocumentTypeAscDisplayOrderAscIdAsc(applicationId)
                .stream()
                .filter(PlaceRegistrationAttachment::isActive)
                .map(MerchantPlaceApplicationAttachmentResponse::from)
                .toList();
    }

    @org.springframework.transaction.annotation.Transactional
    public void delete(Long userId, Long applicationId, Long attachmentId) {
        ownedDraft(userId, applicationId);
        PlaceRegistrationAttachment attachment = attachmentRepository.findByIdAndApplicationId(attachmentId, applicationId)
                .orElseThrow(() -> new PlaceRegistrationException(PlaceRegistrationErrorCode.ATTACHMENT_NOT_FOUND));
        attachmentRepository.delete(attachment);
        deletePublisher.publish(attachment.getStorageKey(), OUTBOX_AGGREGATE_TYPE, applicationId.toString(), "DELETED");
    }

    @org.springframework.transaction.annotation.Transactional
    public void reorder(Long userId, Long applicationId, List<Long> attachmentIds) {
        ownedDraft(userId, applicationId);
        if (attachmentIds == null || attachmentIds.isEmpty() || attachmentIds.size() != new HashSet<>(attachmentIds).size()) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_ATTACHMENT_METADATA);
        }
        List<PlaceRegistrationAttachment> attachments = attachmentRepository
                .findAllByApplicationIdAndDocumentTypeOrderByDisplayOrderAscIdAsc(
                        applicationId,
                        PlaceRegistrationAttachmentType.REPRESENTATIVE_IMAGE
                );
        if (attachments.size() != attachmentIds.size()
                || !new HashSet<>(attachmentIds).equals(attachments.stream().map(PlaceRegistrationAttachment::getId)
                .collect(java.util.stream.Collectors.toSet()))) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_ATTACHMENT_METADATA);
        }
        for (int index = 0; index < attachmentIds.size(); index++) {
            Long attachmentId = attachmentIds.get(index);
            attachments.stream()
                    .filter(attachment -> attachment.getId().equals(attachmentId))
                    .findFirst()
                    .orElseThrow(() -> new PlaceRegistrationException(PlaceRegistrationErrorCode.ATTACHMENT_NOT_FOUND))
                    .changeDisplayOrder(index);
        }
    }

    private PlaceRegistrationApplication ownedDraft(Long userId, Long applicationId) {
        PlaceRegistrationApplication application = owned(userId, applicationId, true);
        if (application.getStatus() != PlaceRegistrationStatus.DRAFT) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        return application;
    }

    private PlaceRegistrationApplication owned(Long userId, Long applicationId) {
        return owned(userId, applicationId, false);
    }

    private PlaceRegistrationApplication owned(Long userId, Long applicationId, boolean lock) {
        PlaceRegistrationApplication application = (lock
                ? applicationRepository.findByIdForUpdate(applicationId)
                : applicationRepository.findById(applicationId))
                .orElseThrow(() -> new PlaceRegistrationException(PlaceRegistrationErrorCode.APPLICATION_NOT_FOUND));
        if (!application.getApplicantUserId().equals(userId)) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.ACCESS_DENIED);
        }
        return application;
    }

    private byte[] validateAndRead(MultipartFile file, PlaceRegistrationAttachmentType documentType) {
        if (file == null || file.isEmpty() || documentType == null) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_ATTACHMENT_METADATA);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.ATTACHMENT_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (!isAllowedContentType(documentType, contentType)) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_ATTACHMENT_METADATA);
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception exception) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_ATTACHMENT_METADATA);
        }
        if (content.length == 0) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_ATTACHMENT_METADATA);
        }
        if (content.length > MAX_FILE_SIZE) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.ATTACHMENT_TOO_LARGE);
        }
        validateSignature(content, contentType);
        return content;
    }

    private boolean isAllowedContentType(PlaceRegistrationAttachmentType type, String contentType) {
        boolean image = "image/jpeg".equals(contentType)
                || "image/png".equals(contentType)
                || "image/webp".equals(contentType);
        return type == PlaceRegistrationAttachmentType.BUSINESS_REGISTRATION
                ? image || "application/pdf".equals(contentType)
                : image;
    }

    private void validateSignature(byte[] bytes, String contentType) {
        boolean jpeg = bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
        boolean png = bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d
                && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
        boolean webp = bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W'
                && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        boolean pdf = bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P'
                && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
        boolean valid = switch (contentType) {
            case "image/jpeg" -> jpeg;
            case "image/png" -> png;
            case "image/webp" -> webp;
            case "application/pdf" -> pdf;
            default -> false;
        };
        if (!valid) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_ATTACHMENT_METADATA);
        }
    }

    private boolean isSensitive(PlaceRegistrationAttachmentType type) {
        return type != PlaceRegistrationAttachmentType.REPRESENTATIVE_IMAGE;
    }

    private LocalDateTime retentionExpiresAt(PlaceRegistrationAttachmentType type, LocalDateTime now) {
        return isSensitive(type) ? now.plusDays(SENSITIVE_RETENTION_DAYS) : null;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("첨부 파일 hash 생성에 실패했습니다.", exception);
        }
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "attachment";
        }
        String normalized = filename.replace('\\', '/');
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        return baseName.isBlank() ? "attachment" : baseName.substring(0, Math.min(baseName.length(), 255));
    }

    private void deleteUploadedObjectOnRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    deleteUploadedObject(key);
                }
            }
        });
    }

    private void deleteUploadedObject(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException exception) {
            log.warn("통합 신청 첨부 업로드 보상 삭제에 실패했습니다.", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
