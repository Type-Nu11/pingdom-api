package com.typenull.pingdom.place.application.service.registration;

import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationAttachmentResponse;
import com.typenull.pingdom.place.domain.exception.PlaceRegistrationErrorCode;
import com.typenull.pingdom.place.domain.exception.PlaceRegistrationException;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationAttachmentRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 민감 첨부의 소속·보존기한 확인과 열람 감사를 담당합니다. */
@Service
@RequiredArgsConstructor
public class MerchantPlaceApplicationAdminAttachmentService {
    private final PlaceRegistrationApplicationRepository applicationRepository;
    private final PlaceRegistrationAttachmentRepository attachmentRepository;
    private final AdminRoleAuthorizationService authorizationService;
    private final AdminAuditLogService auditLogService;
    private final S3ObjectStorage storage;
    private final Clock clock;

    @Transactional
    public List<AdminMerchantPlaceApplicationAttachmentResponse> listAttachmentsForAdmin(Long adminUserId, Long id) {
        authorizationService.requirePermission(adminUserId, AdminPermission.MERCHANT_REVIEW);
        PlaceRegistrationApplication application = unified(id);
        return viewedAttachments(adminUserId, application);
    }

    @Transactional
    public DownloadedAttachment downloadAttachmentForAdmin(Long adminUserId, Long applicationId, Long attachmentId) {
        authorizationService.requirePermission(adminUserId, AdminPermission.MERCHANT_REVIEW);
        PlaceRegistrationApplication application = unified(applicationId);
        PlaceRegistrationAttachment attachment = attachmentRepository.findByIdAndApplicationId(attachmentId, application.getId())
                .orElseThrow(() -> new PlaceRegistrationException(PlaceRegistrationErrorCode.ATTACHMENT_NOT_FOUND));
        if (!attachment.isActive()) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.ATTACHMENT_NOT_FOUND);
        }
        if (attachment.isRetentionExpired(now())) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.ATTACHMENT_RETENTION_EXPIRED);
        }
        byte[] bytes = storage.getBytes(attachment.getStorageKey());
        auditLogService.record(
                adminUserId,
                AdminAuditAction.MERCHANT_PLACE_APPLICATION_ATTACHMENT_VIEWED,
                AdminAuditTargetType.MERCHANT_PLACE_APPLICATION_ATTACHMENT,
                attachment.getId(),
                "통합 신청 민감 첨부 관리자 열람: " + attachment.getDocumentType().name(),
                Map.of(),
                Map.of("applicationId", application.getId())
        );
        return new DownloadedAttachment(bytes, attachment.getContentType());
    }

    // 동일 패키지의 상세 조회가 이미 권한·신청을 확인한 상태에서 감사 중복 없이 조합합니다.
    List<AdminMerchantPlaceApplicationAttachmentResponse> viewedAttachments(
            Long adminUserId, PlaceRegistrationApplication application
    ) {
        List<AdminMerchantPlaceApplicationAttachmentResponse> attachments = attachments(application);
        recordAttachmentMetadataViews(adminUserId, application.getId(), attachments);
        return attachments;
    }

    private PlaceRegistrationApplication unified(Long id) {
        return applicationRepository.findById(id).orElseThrow(this::notFound);
    }

    private PlaceRegistrationException notFound() {
        return new PlaceRegistrationException(PlaceRegistrationErrorCode.APPLICATION_NOT_FOUND);
    }

    private List<AdminMerchantPlaceApplicationAttachmentResponse> attachments(PlaceRegistrationApplication application) {
        return attachmentRepository.findAllByApplicationIdOrderByDocumentTypeAscDisplayOrderAscIdAsc(application.getId())
                .stream()
                .filter(PlaceRegistrationAttachment::isActive)
                .filter(attachment -> !attachment.isRetentionExpired(now()))
                .map(AdminMerchantPlaceApplicationAttachmentResponse::from)
                .toList();
    }

    private void recordAttachmentMetadataViews(
            Long adminUserId,
            Long applicationId,
            List<AdminMerchantPlaceApplicationAttachmentResponse> attachments
    ) {
        attachments.forEach(attachment -> auditLogService.record(
                adminUserId,
                AdminAuditAction.MERCHANT_PLACE_APPLICATION_ATTACHMENT_VIEWED,
                AdminAuditTargetType.MERCHANT_PLACE_APPLICATION_ATTACHMENT,
                attachment.id(),
                "통합 신청 민감 첨부 메타데이터 조회: " + attachment.documentType().name(),
                Map.of(),
                Map.of("applicationId", applicationId)
        ));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public record DownloadedAttachment(byte[] bytes, String contentType) {
    }
}
