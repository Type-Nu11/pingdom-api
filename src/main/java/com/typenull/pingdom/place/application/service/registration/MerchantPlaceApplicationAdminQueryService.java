package com.typenull.pingdom.place.application.service.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.identity.infrastructure.crypto.MerchantVerificationCipher;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationAttachmentResponse;
import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationListItemResponse;
import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationPageResponse;
import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationResponse;
import com.typenull.pingdom.place.domain.exception.PlaceRegistrationErrorCode;
import com.typenull.pingdom.place.domain.exception.PlaceRegistrationException;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 검색·복호화 응답과 민감 상세 열람 감사를 담당합니다. */
@Service
@RequiredArgsConstructor
public class MerchantPlaceApplicationAdminQueryService {
    private final PlaceRegistrationApplicationRepository applicationRepository;
    private final MerchantVerificationCipher verificationCipher;
    private final AdminRoleAuthorizationService authorizationService;
    private final AdminAuditLogService auditLogService;
    private final MerchantPlaceApplicationAdminAttachmentService attachmentAccess;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AdminMerchantPlaceApplicationPageResponse listForAdmin(
            Long adminUserId,
            List<PlaceRegistrationStatus> statuses,
            MerchantPlaceApplicationType applicationType,
            String keyword,
            LocalDateTime submittedFrom,
            LocalDateTime submittedTo,
            int page,
            int limit
    ) {
        authorizationService.requirePermission(adminUserId, AdminPermission.MERCHANT_REVIEW);
        if (submittedFrom != null && submittedTo != null && submittedTo.isBefore(submittedFrom)) {
            throw new AdminException(AdminErrorCode.INVALID_MERCHANT_PLACE_APPLICATION_FILTER_PERIOD);
        }
        List<PlaceRegistrationStatus> effectiveStatuses = statuses == null || statuses.isEmpty()
                ? List.of(PlaceRegistrationStatus.values())
                : statuses;
        Page<PlaceRegistrationApplication> result = applicationRepository.findAll(
                adminListSpecification(effectiveStatuses, applicationType, normalizeKeyword(keyword), submittedFrom, submittedTo),
                pageable(page, limit)
        );
        return new AdminMerchantPlaceApplicationPageResponse(
                result.getContent().stream()
                        .map(application -> AdminMerchantPlaceApplicationListItemResponse.from(
                                application,
                                decryptRegistrationNumber(application)
                        ))
                        .toList(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    // 상세 조회는 민감정보 열람 감사를 저장하므로 쓰기 트랜잭션에 참여합니다.
    @Transactional
    public AdminMerchantPlaceApplicationResponse getForAdmin(Long adminUserId, Long id) {
        authorizationService.requirePermission(adminUserId, AdminPermission.MERCHANT_REVIEW);
        PlaceRegistrationApplication application = unified(id);
        auditLogService.record(
                adminUserId,
                AdminAuditAction.MERCHANT_PLACE_APPLICATION_VIEWED,
                AdminAuditTargetType.MERCHANT_PLACE_APPLICATION,
                application.getId(),
                "통합 신청 민감정보 상세 조회",
                Map.of(),
                Map.of()
        );
        List<AdminMerchantPlaceApplicationAttachmentResponse> attachments =
                attachmentAccess.viewedAttachments(adminUserId, application);
        return AdminMerchantPlaceApplicationResponse.from(
                application,
                decryptRegistrationNumber(application),
                attachments,
                objectMapper
        );
    }

    private PlaceRegistrationApplication unified(Long id) {
        return applicationRepository.findById(id).orElseThrow(this::notFound);
    }

    private PlaceRegistrationException notFound() {
        return new PlaceRegistrationException(PlaceRegistrationErrorCode.APPLICATION_NOT_FOUND);
    }

    private String decryptRegistrationNumber(PlaceRegistrationApplication application) {
        return verificationCipher.decrypt(application.getEncryptedBusinessRegistrationNumber());
    }

    private Specification<PlaceRegistrationApplication> adminListSpecification(
            List<PlaceRegistrationStatus> statuses,
            MerchantPlaceApplicationType applicationType,
            String keyword,
            LocalDateTime submittedFrom,
            LocalDateTime submittedTo
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("status").in(statuses));
            if (applicationType != null) {
                predicates.add(builder.equal(root.get("applicationType"), applicationType));
            }
            if (keyword != null) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                var applicantSubquery = query.subquery(User.class);
                var applicant = applicantSubquery.from(User.class);
                applicantSubquery.select(applicant).where(builder.and(
                        builder.equal(applicant.get("id"), root.get("applicantUserId")),
                        builder.like(builder.lower(applicant.get("username")), pattern)
                ));
                var existingPlaceSubquery = query.subquery(MapPlace.class);
                var existingPlace = existingPlaceSubquery.from(MapPlace.class);
                existingPlaceSubquery.select(existingPlace).where(builder.and(
                        builder.equal(existingPlace.get("id"), root.get("existingPlaceId")),
                        builder.like(builder.lower(existingPlace.get("name")), pattern)
                ));
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("placeName")), pattern),
                        builder.exists(applicantSubquery),
                        builder.exists(existingPlaceSubquery)
                ));
            }
            if (submittedFrom != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("submittedAt"), submittedFrom));
            }
            if (submittedTo != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("submittedAt"), submittedTo));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private PageRequest pageable(int page, int limit) {
        return PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100),
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmedKeyword = keyword.trim();
        return trimmedKeyword.isEmpty() ? null : trimmedKeyword;
    }
}
