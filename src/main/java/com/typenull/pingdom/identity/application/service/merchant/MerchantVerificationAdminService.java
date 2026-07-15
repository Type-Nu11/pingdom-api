package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.AdminMerchantVerificationPageResponse;
import com.typenull.pingdom.identity.api.dto.merchant.AdminMerchantVerificationListItemResponse;
import com.typenull.pingdom.identity.api.dto.merchant.AdminMerchantVerificationResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantVerificationReviewRequest;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.infrastructure.crypto.MerchantVerificationCipher;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantVerificationAdminService {

    private final MerchantVerificationRepository verificationRepository;
    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantVerificationCipher verificationCipher;
    private final AdminAuditLogService auditLogService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminMerchantVerificationPageResponse list(
            MerchantVerificationStatus identityStatus,
            MerchantVerificationStatus businessStatus,
            int page,
            int limit
    ) {
        PageRequest pageable = PageRequest.of(
                Math.max(page - 1, 0),
                Math.min(Math.max(limit, 1), 100),
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("userId"))
        );
        Page<MerchantVerification> result;
        if (identityStatus != null && businessStatus != null) {
            result = verificationRepository.findAllByIdentityStatusAndBusinessStatus(
                    identityStatus,
                    businessStatus,
                    pageable
            );
        } else if (identityStatus != null) {
            result = verificationRepository.findAllByIdentityStatus(identityStatus, pageable);
        } else if (businessStatus != null) {
            result = verificationRepository.findAllByBusinessStatus(businessStatus, pageable);
        } else {
            result = verificationRepository.findAll(pageable);
        }
        return new AdminMerchantVerificationPageResponse(
                result.getContent().stream()
                        .map(verification -> AdminMerchantVerificationListItemResponse.from(
                                verification,
                                decryptRegistrationNumber(verification)
                        ))
                        .toList(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public AdminMerchantVerificationResponse get(Long adminUserId, Long userId) {
        MerchantVerification verification = verificationRepository.findById(userId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.VERIFICATION_NOT_FOUND));
        auditLogService.record(
                adminUserId,
                AdminAuditAction.MERCHANT_VERIFICATION_VIEWED,
                AdminAuditTargetType.MERCHANT_VERIFICATION,
                userId,
                "Merchant 검증 민감정보 상세 조회",
                Map.of(),
                Map.of()
        );
        return response(verification);
    }

    @Transactional
    public AdminMerchantVerificationResponse review(
            Long adminUserId,
            Long userId,
            MerchantVerificationReviewRequest request
    ) {
        MerchantOwnerProfile profile = profileRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.PROFILE_NOT_FOUND));
        MerchantVerification verification = verificationRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.VERIFICATION_NOT_FOUND));
        if (!verification.matchesBusinessName(profile.getBusinessName())) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.VERIFICATION_REQUIRED);
        }
        MerchantVerificationStatus beforeIdentityStatus = verification.getIdentityStatus();
        MerchantVerificationStatus beforeBusinessStatus = verification.getBusinessStatus();

        // 외부 검증 제공자가 정해지기 전까지 운영을 이어가기 위한 임시 수동 심사다.
        // 현재는 관리자 판단을 신뢰하므로 오판 위험이 남으며, 향후 제공자 결과와 원문 대조 이력으로 교체한다.
        try {
            verification.review(
                    adminUserId,
                    Boolean.TRUE.equals(request.identityApproved()),
                    Boolean.TRUE.equals(request.businessApproved()),
                    request.reason().trim(),
                    LocalDateTime.now(clock)
            );
        } catch (IllegalStateException exception) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.INVALID_VERIFICATION_STATE);
        }

        auditLogService.record(
                adminUserId,
                AdminAuditAction.MERCHANT_VERIFICATION_REVIEWED,
                AdminAuditTargetType.MERCHANT_VERIFICATION,
                userId,
                request.reason().trim(),
                Map.of(
                        "identityStatus", beforeIdentityStatus,
                        "businessStatus", beforeBusinessStatus
                ),
                Map.of(
                        "identityStatus", verification.getIdentityStatus(),
                        "businessStatus", verification.getBusinessStatus()
                )
        );
        return response(verification);
    }

    private AdminMerchantVerificationResponse response(MerchantVerification verification) {
        return AdminMerchantVerificationResponse.from(verification, decryptRegistrationNumber(verification));
    }

    private String decryptRegistrationNumber(MerchantVerification verification) {
        return verificationCipher.decrypt(verification.getEncryptedBusinessRegistrationNumber());
    }
}
