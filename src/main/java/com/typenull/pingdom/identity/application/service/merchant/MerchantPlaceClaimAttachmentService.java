package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceClaimAttachmentResponse;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaim;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimAttachment;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimAttachmentType;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceClaimAttachmentRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceClaimRepository;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MerchantPlaceClaimAttachmentService {
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private final MerchantPlaceClaimRepository claimRepository;
    private final MerchantPlaceClaimAttachmentRepository attachmentRepository;
    private final S3ObjectStorage storage;
    private final S3ObjectDeleteOutboxPublisher deletePublisher;
    private final Clock clock;
    private final AdminAuditLogService auditLogService;
    private final MerchantPlaceClaimAttachmentMalwareScanner malwareScanner;

    @Transactional
    public MerchantPlaceClaimAttachmentResponse upload(Long userId, Long claimId,
            MerchantPlaceClaimAttachmentType type, MultipartFile file) {
        MerchantPlaceClaim claim = ownedClaim(userId, claimId);
        validate(file, type);
        byte[] bytes = read(file);
        validateSignature(bytes, file.getContentType(), type);
        malwareScanner.scan(bytes);
        String hash = sha256(bytes);
        List<MerchantPlaceClaimAttachment> existing = attachmentRepository
                .findAllByClaimIdAndDocumentTypeOrderByDisplayOrderAscIdAsc(claimId, type);
        if (existing.stream().anyMatch(item -> item.getFileHash().equals(hash))) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.CLAIM_ATTACHMENT_DUPLICATE);
        }
        if (type.isSensitive() && !existing.isEmpty()) {
            MerchantPlaceClaimAttachment old = existing.get(0);
            attachmentRepository.delete(old);
            deletePublisher.publish(old.getStorageKey(), "MERCHANT_PLACE_CLAIM_ATTACHMENT", claimId.toString(), "REPLACED");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        S3ObjectStorage.S3PutResult uploaded = storage.putPrivate(bytes, file.getContentType(),
                "private/merchant-place-claims/" + claimId + "/" + type.name().toLowerCase());
        MerchantPlaceClaimAttachment saved = attachmentRepository.save(MerchantPlaceClaimAttachment.create(
                claimId, type, uploaded.key(), safeFilename(file.getOriginalFilename()),
                file.getContentType(), bytes.length, hash, existing.size(), now));
        return MerchantPlaceClaimAttachmentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<MerchantPlaceClaimAttachmentResponse> list(Long userId, Long claimId) {
        ownedClaim(userId, claimId);
        return attachmentRepository.findAllByClaimIdOrderByDocumentTypeAscDisplayOrderAscIdAsc(claimId)
                .stream().map(MerchantPlaceClaimAttachmentResponse::from).toList();
    }

    @Transactional
    public void delete(Long userId, Long claimId, Long attachmentId) {
        ownedClaim(userId, claimId);
        MerchantPlaceClaimAttachment attachment = attachmentRepository.findByIdAndClaimId(attachmentId, claimId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.CLAIM_ATTACHMENT_NOT_FOUND));
        attachmentRepository.delete(attachment);
        deletePublisher.publish(attachment.getStorageKey(), "MERCHANT_PLACE_CLAIM_ATTACHMENT", claimId.toString(), "DELETED");
    }

    @Transactional
    public void reorder(Long userId, Long claimId, List<Long> attachmentIds) {
        ownedClaim(userId, claimId);
        List<MerchantPlaceClaimAttachment> attachments = attachmentRepository
                .findAllByClaimIdAndDocumentTypeOrderByDisplayOrderAscIdAsc(claimId,
                        MerchantPlaceClaimAttachmentType.REPRESENTATIVE_IMAGE);
        if (attachments.size() != attachmentIds.size()
                || !new HashSet<>(attachmentIds).equals(attachments.stream().map(MerchantPlaceClaimAttachment::getId).collect(java.util.stream.Collectors.toSet()))) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.CLAIM_ATTACHMENT_INVALID);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        for (int index = 0; index < attachmentIds.size(); index++) {
            Long attachmentId = attachmentIds.get(index);
            attachments.stream().filter(item -> item.getId().equals(attachmentId)).findFirst()
                    .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.CLAIM_ATTACHMENT_NOT_FOUND))
                    .changeDisplayOrder(index, now);
        }
    }

    @Transactional
    public DownloadedAttachment downloadForAdmin(Long adminUserId, Long claimId, Long attachmentId) {
        MerchantPlaceClaimAttachment attachment = attachmentRepository.findByIdAndClaimId(attachmentId, claimId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.CLAIM_ATTACHMENT_NOT_FOUND));
        byte[] bytes = storage.getBytes(attachment.getStorageKey());
        auditLogService.record(adminUserId, AdminAuditAction.MERCHANT_PLACE_CLAIM_ATTACHMENT_VIEWED,
                AdminAuditTargetType.MERCHANT_PLACE_CLAIM_ATTACHMENT, claimId,
                "민감 첨부 관리자 열람: " + attachment.getDocumentType().name(), null, null);
        return new DownloadedAttachment(bytes, attachment.getContentType());
    }

    private MerchantPlaceClaim ownedClaim(Long userId, Long claimId) {
        MerchantPlaceClaim claim = claimRepository.findByIdAndMerchantOwnerUserId(claimId, userId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_CLAIM_NOT_FOUND));
        if (claim.getStatus() != MerchantPlaceClaimStatus.PENDING) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.INVALID_PLACE_CLAIM_STATE);
        }
        return claim;
    }

    private void validate(MultipartFile file, MerchantPlaceClaimAttachmentType type) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE || type == null) {
            throw new MerchantOwnerException(file != null && file.getSize() > MAX_FILE_SIZE
                    ? MerchantOwnerErrorCode.CLAIM_ATTACHMENT_TOO_LARGE : MerchantOwnerErrorCode.CLAIM_ATTACHMENT_INVALID);
        }
        String contentType = file.getContentType();
        boolean image = contentType != null && List.of("image/jpeg", "image/png", "image/webp").contains(contentType);
        boolean pdf = "application/pdf".equals(contentType);
        if ((type == MerchantPlaceClaimAttachmentType.BUSINESS_LICENSE && !(image || pdf))
                || (type != MerchantPlaceClaimAttachmentType.BUSINESS_LICENSE && !image)) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.CLAIM_ATTACHMENT_INVALID);
        }
    }

    private byte[] read(MultipartFile file) {
        try { return file.getBytes(); } catch (Exception exception) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.CLAIM_ATTACHMENT_INVALID);
        }
    }

    private void validateSignature(byte[] bytes, String contentType, MerchantPlaceClaimAttachmentType type) {
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
        boolean valid = "application/pdf".equals(contentType) ? pdf : jpeg || png || webp;
        if (type != MerchantPlaceClaimAttachmentType.BUSINESS_LICENSE && pdf) valid = false;
        if (!valid) throw new MerchantOwnerException(MerchantOwnerErrorCode.CLAIM_ATTACHMENT_INVALID);
    }

    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception exception) { throw new IllegalStateException("첨부 hash 생성에 실패했습니다.", exception); }
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "attachment";
        String normalized = filename.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1, Math.min(normalized.length(), 255));
    }

    public record DownloadedAttachment(byte[] bytes, String contentType) {}
}
