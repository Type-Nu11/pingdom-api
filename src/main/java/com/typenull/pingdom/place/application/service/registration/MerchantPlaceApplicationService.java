package com.typenull.pingdom.place.application.service.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.application.service.merchant.MerchantApplicationApprovalService;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitationStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceInvitationRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMemberRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.infrastructure.crypto.MerchantVerificationCipher;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.offer.domain.TouristOffer;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationAttachmentResponse;
import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationPageResponse;
import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationResponse;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationPageResponse;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationRequest;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationResponse;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationReviewRequest;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationRequest;
import com.typenull.pingdom.place.application.service.registration.MerchantPlaceApplicationReviewSnapshotSerializer.ReviewSnapshots;
import com.typenull.pingdom.place.domain.exception.PlaceRegistrationErrorCode;
import com.typenull.pingdom.place.domain.exception.PlaceRegistrationException;
import com.typenull.pingdom.place.domain.place.category.PlaceCategoryPolicy;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationReviewHistory;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationCategory;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.MerchantPlaceApplicationReviewHistoryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Web의 사업자 검증과 장소 등록/소유권 신청을 단일 심사 단위로 관리.
 * 기존 장소 등록 서비스는 신규 장소 생성과 운영시간 반영 책임을 계속 보유.
 */
@Service
@RequiredArgsConstructor
public class MerchantPlaceApplicationService {

    private final PlaceRegistrationApplicationRepository applicationRepository;
    private final MapPlaceRepository placeRepository;
    private final PlaceRegistrationService placeRegistrationService;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;
    private final MerchantPlaceMemberRepository memberRepository;
    private final MerchantPlaceInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final UserAccessStatusService userAccessStatusService;
    private final MerchantVerificationCipher verificationCipher;
    private final AdminRoleAuthorizationService authorizationService;
    private final AdminAuditLogService auditLogService;
    private final TouristOfferRepository touristOfferRepository;
    private final MerchantPlaceApplicationReviewHistoryRepository reviewHistoryRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MerchantPlaceApplicationAdminQueryService adminQuery;
    private final MerchantPlaceApplicationAdminAttachmentService adminAttachments;
    private final MerchantPlaceApplicationReviewSnapshotSerializer snapshotSerializer;
    private final MerchantApplicationApprovalService identityApproval;

    /**
     * NEW_PLACE는 장소 등록 서비스에 초안 생성을 위임하고 Claim은 기존 장소 정보로 초안을 만든 뒤 사업자 정보를 반영.
     * 반환 시점은 제출 전이며 유형별 필수 장소 정보와 사업자 데이터 검증 실패는 전체 초안 저장을 롤백시킴.
     */
    @Transactional
    public MerchantPlaceApplicationResponse create(Long userId, MerchantPlaceApplicationRequest request) {
        PlaceRegistrationApplication application;
        if (request.applicationType() == MerchantPlaceApplicationType.NEW_PLACE) {
            PlaceRegistrationRequest newPlace = requireNewPlace(request);
            Long id = placeRegistrationService.createForUnifiedApplication(userId, newPlace);
            application = locked(id);
        } else {
            application = createClaimDraft(userId, request, now());
            applicationRepository.save(application);
        }
        applyMerchantData(application, request, now());
        return response(application);
    }

    @Transactional(readOnly = true)
    public MerchantPlaceApplicationPageResponse list(Long userId, int page, int limit) {
        return page(applicationRepository.findAllByApplicantUserId(userId, pageable(page, limit)));
    }

    @Transactional(readOnly = true)
    public MerchantPlaceApplicationPageResponse listAll(int page, int limit) {
        return page(applicationRepository.findAll(pageable(page, limit)));
    }

    /**
     * MERCHANT_REVIEW 권한을 확인하고 상태·유형·장소/신청자 키워드·제출 기간으로 신청을 조회.
     * 빈 상태 목록은 모든 상태, 제출 기간 양 끝은 포함하며 역전된 기간은 거절. 갱신 시각·ID 역순으로 페이지를 반환하고 사업자번호를 복호화.
     */
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
        return adminQuery.listForAdmin(adminUserId, statuses, applicationType, keyword, submittedFrom, submittedTo, page, limit);
    }

    /**
     * 신청 ID와 신청자가 모두 일치하는 한 건을 응답으로 변환.
     * 타인 신청과 존재하지 않는 신청을 모두 APPLICATION_NOT_FOUND로 처리.
     */
    @Transactional(readOnly = true)
    public MerchantPlaceApplicationResponse get(Long userId, Long id) {
        PlaceRegistrationApplication application = applicationRepository.findByIdAndApplicantUserId(id, userId)
                .orElseThrow(this::notFound);
        return response(application);
    }

    @Transactional(readOnly = true)
    public MerchantPlaceApplicationResponse getAny(Long id) {
        PlaceRegistrationApplication application = applicationRepository.findById(id).orElseThrow(this::notFound);
        return response(application);
    }

    /**
     * MERCHANT_REVIEW 권한을 검사한 뒤 복호화한 사업자등록번호와 보존 기한 내 활성 첨부를 반환.
     * 민감정보 및 첨부 메타데이터 조회 감사 기록을 함께 저장하므로 쓰기 트랜잭션 사용.
     */
    @Transactional
    public AdminMerchantPlaceApplicationResponse getForAdmin(Long adminUserId, Long id) {
        return adminQuery.getForAdmin(adminUserId, id);
    }

    /**
     * 심사 권한을 확인하고 통합 신청의 활성·보존 기한 내 첨부 메타데이터를 문서 유형·표시 순서·ID 순으로 반환.
     * 반환되는 첨부별 관리자 열람 감사 기록을 저장하므로 쓰기 트랜잭션 사용.
     */
    @Transactional
    public List<AdminMerchantPlaceApplicationAttachmentResponse> listAttachmentsForAdmin(Long adminUserId, Long id) {
        return adminAttachments.listAttachmentsForAdmin(adminUserId, id);
    }

    /**
     * 심사 권한과 신청 소속·활성 여부·보존 기한을 확인한 첨부의 S3 바이트와 MIME을 반환.
     * 객체 조회 성공 후 열람 감사 기록을 저장하며 비활성·기한 만료·없는 첨부는 거절하고 저장소 실패는 전파.
     */
    @Transactional
    public DownloadedAttachment downloadAttachmentForAdmin(Long adminUserId, Long applicationId, Long attachmentId) {
        var attachment = adminAttachments.downloadAttachmentForAdmin(adminUserId, applicationId, attachmentId);
        return new DownloadedAttachment(attachment.bytes(), attachment.contentType());
    }

    /**
     * 본인 신청을 잠근 뒤 신청 유형 변경을 거절하고 초안의 장소 정보 또는 Claim 스냅샷과 사업자 정보를 함께 갱신.
     * 잘못된 값은 INVALID_ATTACHMENT_METADATA, 허용되지 않는 상태는 INVALID_STATE로 변환하며 갱신된 신청 응답을 반환.
     */
    @Transactional
    public MerchantPlaceApplicationResponse update(Long userId, Long id, MerchantPlaceApplicationRequest request) {
        PlaceRegistrationApplication application = mine(userId, id);
        try {
            if (application.getApplicationType() != request.applicationType()) {
                throw new IllegalStateException("신청 유형은 초안 생성 후 변경할 수 없습니다.");
            }
            if (request.applicationType() == MerchantPlaceApplicationType.NEW_PLACE) {
                placeRegistrationService.updateForUnifiedApplication(userId, id, requireNewPlace(request));
            } else {
                refreshClaimSnapshot(application, request, now());
            }
            applyMerchantData(application, request, now());
        } catch (IllegalArgumentException exception) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_ATTACHMENT_METADATA);
        } catch (IllegalStateException exception) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        return response(application);
    }

    /**
     * 신청자와 신청 행을 잠그고 필수 사업자 정보·첨부를 검증하여 심사 대기로 전이.
     * 기존 장소 Claim은 장소와 소유권도 잠가 현재 소유자를 다시 저장하고 같은 장소의 대기 신청을 거절.
     */
    @Transactional
    public MerchantPlaceApplicationResponse submit(Long userId, Long id) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new PlaceRegistrationException(PlaceRegistrationErrorCode.ACCESS_DENIED));
        PlaceRegistrationApplication application = mine(userId, id);
        requireMerchantData(application);
        if (application.getApplicationType() == MerchantPlaceApplicationType.NEW_PLACE) {
            placeRegistrationService.submitForUnifiedApplication(userId, id);
        } else {
            submitExistingPlaceClaim(application);
        }
        applicationRepository.flush();
        return response(application);
    }

    /**
     * 본인 신청을 잠가 DRAFT 또는 PENDING에서 CANCELED로 전이하고 취소 시각을 기록.
     * 그 밖의 상태는 INVALID_STATE로 거절하며 첨부 파일의 즉시 삭제는 미수행.
     */
    @Transactional
    public MerchantPlaceApplicationResponse cancel(Long userId, Long id) {
        PlaceRegistrationApplication application = mine(userId, id);
        try {
            application.cancel(now());
        } catch (IllegalStateException exception) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        return response(application);
    }

    /**
     * 본인 신청을 잠가 REJECTED에서 DRAFT로 되돌리고 이전 심사자·사유·제출 해시와 심사 시각을 비움.
     * 첨부와 장소 입력은 유지하여 수정 후 재제출에 사용하며 다른 상태는 INVALID_STATE로 거절.
     */
    @Transactional
    public MerchantPlaceApplicationResponse reopen(Long userId, Long id) {
        PlaceRegistrationApplication application = mine(userId, id);
        try {
            application.reopen(now());
        } catch (IllegalStateException exception) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        return response(application);
    }

    /** 심사 승인과 사업자 활성화, 장소 생성 또는 소유권 이전을 하나의 트랜잭션에서 완료. */
    @Transactional
    public MerchantPlaceApplicationResponse approve(Long adminUserId, Long id, MerchantPlaceApplicationReviewRequest request) {
        authorizationService.requirePermission(adminUserId, AdminPermission.MERCHANT_REVIEW);
        PlaceRegistrationApplication application = locked(id);
        if (application.getStatus() != PlaceRegistrationStatus.PENDING) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        if (!application.matchesVersion(request.reviewedVersion())) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.STALE_REVIEW_VERSION);
        }
        LocalDateTime now = now();
        PlaceRegistrationStatus beforeStatus = application.getStatus();
        String reason = normalizedReason(request);
        ClaimReviewContext claimReviewContext = application.getApplicationType() == MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM
                ? captureClaimReviewContext(application)
                : null;
        Long previousOwnerUserIdToCloseOffers = null;
        try {
            application.approve(adminUserId, reason, now);
            prepareMerchantVerification(application, now);
            if (application.getApplicationType() == MerchantPlaceApplicationType.NEW_PLACE) {
                Long placeId = placeRegistrationService.createApprovedPlaceForUnifiedApplication(
                        application.getApplicantUserId(), application.getId());
                application.complete(placeId, now);
            } else {
                activateMerchant(application, adminUserId, now);
                previousOwnerUserIdToCloseOffers = transferExistingPlace(application, now);
                application.complete(application.getExistingPlaceId(), now);
            }
            approveVerification(application, adminUserId, now);
            if (previousOwnerUserIdToCloseOffers != null) {
                touristOfferRepository.closeAllByMerchantOwnerUserIdAndPlaceIdIn(
                        previousOwnerUserIdToCloseOffers,
                        Set.of(application.getExistingPlaceId()),
                        now
                );
            }
            if (application.getApplicationType() == MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM) {
                userAccessStatusService.evict(application.getApplicantUserId());
            }
        } catch (IllegalStateException exception) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        audit(adminUserId, application, AdminAuditAction.MERCHANT_PLACE_APPLICATION_APPROVED, reason);
        saveReviewHistory(
                application,
                adminUserId,
                beforeStatus,
                request.reviewedVersion(),
                reason,
                claimReviewContext == null ? ReviewSnapshots.empty() : claimReviewContext.snapshots(),
                now
        );
        applicationRepository.flush();
        return response(application);
    }

    /**
     * 심사 권한을 확인하고 신청 행을 잠가 PENDING 상태와 화면에서 확인한 version의 일치를 요구.
     * 반려 사유를 정규화하여 상태를 바꾸고 감사 로그·심사 이력을 같은 트랜잭션에 저장하며 상태·버전 충돌은 거절.
     */
    @Transactional
    public MerchantPlaceApplicationResponse reject(Long adminUserId, Long id, MerchantPlaceApplicationReviewRequest request) {
        authorizationService.requirePermission(adminUserId, AdminPermission.MERCHANT_REVIEW);
        PlaceRegistrationApplication application = locked(id);
        if (application.getStatus() != PlaceRegistrationStatus.PENDING) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        if (!application.matchesVersion(request.reviewedVersion())) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.STALE_REVIEW_VERSION);
        }
        LocalDateTime now = now();
        PlaceRegistrationStatus beforeStatus = application.getStatus();
        String reason = normalizedReason(request);
        try {
            application.reject(adminUserId, reason, now);
        } catch (IllegalStateException exception) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        audit(adminUserId, application, AdminAuditAction.MERCHANT_PLACE_APPLICATION_REJECTED, reason);
        saveReviewHistory(
                application,
                adminUserId,
                beforeStatus,
                request.reviewedVersion(),
                reason,
                ReviewSnapshots.empty(),
                now
        );
        return response(application);
    }

    private PlaceRegistrationApplication createClaimDraft(Long userId, MerchantPlaceApplicationRequest request, LocalDateTime now) {
        MapPlace place = requirePlace(request.existingPlaceId());
        PlaceRegistrationApplication application = PlaceRegistrationApplication.draft(
                userId, place.getName(), registrationCategory(place.getCategory()), place.getLatitude(), place.getLongitude(),
                textOr(place.getRoadAddress(), place.getAddress()), textOr(place.getJibunAddress(), place.getAddress()),
                textOr(place.getPostalCode(), "UNKNOWN"), textOr(request.claimReason(), "기존 장소 소유권 신청"), now
        );
        application.updateContactPhones(normalizePhone(request.merchantContactPhone()), normalizePhone(request.merchantContactPhone()));
        return application;
    }

    private void refreshClaimSnapshot(PlaceRegistrationApplication application, MerchantPlaceApplicationRequest request, LocalDateTime now) {
        MapPlace place = requirePlace(request.existingPlaceId());
        application.update(place.getName(), registrationCategory(place.getCategory()),
                place.getLatitude(), place.getLongitude(), textOr(place.getRoadAddress(), place.getAddress()),
                textOr(place.getJibunAddress(), place.getAddress()), textOr(place.getPostalCode(), "UNKNOWN"),
                textOr(request.claimReason(), "기존 장소 소유권 신청"), now);
        application.updateContactPhones(normalizePhone(request.merchantContactPhone()), normalizePhone(request.merchantContactPhone()));
    }

    private void applyMerchantData(PlaceRegistrationApplication application, MerchantPlaceApplicationRequest request, LocalDateTime now) {
        application.configureMerchantSubmission(request.applicationType(), request.legalName(), request.businessName(),
                verificationCipher.encrypt(normalizeBusinessRegistrationNumber(request.businessRegistrationNumber())),
                request.merchantDisplayName(), request.merchantContactEmail(), request.merchantDescription(),
                normalizePhone(request.merchantContactPhone()), request.existingPlaceId(),
                ownershipSnapshot(request), request.claimReason(), now);
    }

    private void prepareMerchantVerification(PlaceRegistrationApplication application, LocalDateTime now) {
        identityApproval.prepare(new MerchantApplicationApprovalService.MerchantData(
                application.getApplicantUserId(), application.getLegalName(), application.getBusinessName(),
                application.getEncryptedBusinessRegistrationNumber(), application.getMerchantDisplayName(),
                application.getMerchantDescription(), application.getMerchantContactEmail(), application.getMerchantContactPhone()
        ), now);
    }

    private void activateMerchant(PlaceRegistrationApplication application, Long adminUserId, LocalDateTime now) {
        try {
            identityApproval.activate(application.getApplicantUserId(), adminUserId, application.getReviewReason(), now);
        } catch (MerchantOwnerException exception) {
            if (exception.getErrorCode() != MerchantOwnerErrorCode.PROFILE_NOT_FOUND) {
                throw exception;
            }
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.MERCHANT_PROFILE_REQUIRED);
        } catch (UsersException exception) {
            if (exception.getErrorCode() != UsersErrorCode.USER_NOT_FOUND) {
                throw exception;
            }
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.ACCESS_DENIED);
        }
    }

    private void approveVerification(PlaceRegistrationApplication application, Long adminUserId, LocalDateTime now) {
        try {
            identityApproval.approveVerification(
                    application.getApplicantUserId(), adminUserId, application.getReviewReason(), now
            );
        } catch (MerchantOwnerException exception) {
            if (exception.getErrorCode() != MerchantOwnerErrorCode.VERIFICATION_NOT_FOUND) {
                throw exception;
            }
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.MERCHANT_PROFILE_REQUIRED);
        }
    }

    private Long transferExistingPlace(PlaceRegistrationApplication application, LocalDateTime now) {
        try {
            return identityApproval.transferPlaceOwnership(
                    application.getExistingPlaceId(), application.getPreviousOwnerUserId(),
                    application.getApplicantUserId(), now
            );
        } catch (MerchantOwnerException exception) {
            if (exception.getErrorCode() != MerchantOwnerErrorCode.PLACE_OWNERSHIP_CHANGED) {
                throw exception;
            }
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
    }

    private void requireMerchantData(PlaceRegistrationApplication application) {
        if (application.getLegalName() == null || application.getBusinessName() == null
                || application.getEncryptedBusinessRegistrationNumber() == null || application.getMerchantDisplayName() == null
                || application.getMerchantContactEmail() == null || application.getMerchantContactPhone() == null) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
    }

    private void submitExistingPlaceClaim(PlaceRegistrationApplication application) {
        Long placeId = application.getExistingPlaceId();
        placeRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new PlaceRegistrationException(PlaceRegistrationErrorCode.APPLICATION_NOT_FOUND));
        MerchantOwnerPlace currentOwner = ownerPlaceRepository.findByPlaceIdForUpdate(placeId).orElse(null);
        Long currentOwnerUserId = currentOwner == null ? null : currentOwner.getMerchantOwnerUserId();
        if (Objects.equals(currentOwnerUserId, application.getApplicantUserId())) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.DUPLICATE_PLACE);
        }
        if (applicationRepository.existsByExistingPlaceIdAndApplicationTypeAndStatus(
                placeId,
                MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM,
                PlaceRegistrationStatus.PENDING
        )) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.DUPLICATE_PLACE);
        }
        LocalDateTime now = now();
        application.refreshClaimOwnershipSnapshot(currentOwnerUserId, now);
        try {
            application.submit(now);
        } catch (IllegalStateException exception) {
            throw new PlaceRegistrationException(application.hasRequiredFiles(now)
                    ? PlaceRegistrationErrorCode.INVALID_STATE : PlaceRegistrationErrorCode.REQUIRED_FILES_MISSING);
        }
        try {
            applicationRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.DUPLICATE_PLACE);
        }
    }

    private PlaceRegistrationRequest requireNewPlace(MerchantPlaceApplicationRequest request) {
        if (request.newPlace() == null) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        return request.newPlace();
    }

    private MapPlace requirePlace(Long placeId) {
        if (placeId == null) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.APPLICATION_NOT_FOUND);
        }
        return placeRepository.findById(placeId).orElseThrow(() -> new PlaceRegistrationException(PlaceRegistrationErrorCode.APPLICATION_NOT_FOUND));
    }

    private PlaceRegistrationApplication mine(Long userId, Long id) {
        PlaceRegistrationApplication application = locked(id);
        if (!application.getApplicantUserId().equals(userId)) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.ACCESS_DENIED);
        }
        return application;
    }

    private PlaceRegistrationApplication unified(Long id) {
        return applicationRepository.findById(id).orElseThrow(this::notFound);
    }

    private PlaceRegistrationApplication locked(Long id) {
        return applicationRepository.findByIdForUpdate(id).orElseThrow(this::notFound);
    }

    private PlaceRegistrationException notFound() {
        return new PlaceRegistrationException(PlaceRegistrationErrorCode.APPLICATION_NOT_FOUND);
    }

    private void audit(Long adminUserId, PlaceRegistrationApplication application, AdminAuditAction action, String reason) {
        auditLogService.record(adminUserId, action, AdminAuditTargetType.MERCHANT_PLACE_APPLICATION, application.getId(), reason,
                Map.of("status", PlaceRegistrationStatus.PENDING),
                Map.of("status", application.getStatus(), "applicationType", application.getApplicationType(),
                        "placeId", String.valueOf(application.getCompletedPlaceId())));
    }

    private MerchantPlaceApplicationResponse response(PlaceRegistrationApplication application) {
        return MerchantPlaceApplicationResponse.from(application, objectMapper);
    }

    private MerchantPlaceApplicationPageResponse page(Page<PlaceRegistrationApplication> result) {
        return new MerchantPlaceApplicationPageResponse(result.getContent().stream().map(this::response).toList(), result.getNumber() + 1,
                result.getSize(), result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    private PageRequest pageable(int page, int limit) {
        return PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100),
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
    }

    private String normalizedReason(MerchantPlaceApplicationReviewRequest request) {
        return request.reason() == null ? "" : request.reason().trim();
    }

    private String normalizeBusinessRegistrationNumber(String value) {
        String normalized = value.replaceAll("[\\s-]", "");
        if (!normalized.matches("[0-9A-Za-z]{8,20}")) {
            throw new IllegalArgumentException("사업자등록번호 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private String normalizePhone(String value) {
        String normalized = value == null ? "" : value.replaceAll("[\\s-]", "");
        if (!normalized.matches("\\+[1-9]\\d{7,14}")) {
            throw new IllegalArgumentException("전화번호 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private PlaceRegistrationCategory registrationCategory(String category) {
        String normalized = PlaceCategoryPolicy.normalize(category);
        return PlaceRegistrationCategory.valueOf(normalized == null ? PlaceCategoryPolicy.OTHER : normalized);
    }

    private Long ownershipSnapshot(MerchantPlaceApplicationRequest request) {
        if (request.applicationType() != MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM) {
            return null;
        }
        return ownerPlaceRepository.findById(request.existingPlaceId())
                .map(MerchantOwnerPlace::getMerchantOwnerUserId)
                .orElse(null);
    }

    /**
     * Claim 승인 전에 장소·소유권을 잠그고 제출 당시 소유자와 일치하는지 확인.
     * 이전 팀과 대기 초대, 잠금 조회한 이전 소유자의 오퍼를 변경 전 감사 스냅샷으로 보존.
     */
    private ClaimReviewContext captureClaimReviewContext(PlaceRegistrationApplication application) {
        Long placeId = application.getExistingPlaceId();
        placeRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new PlaceRegistrationException(PlaceRegistrationErrorCode.APPLICATION_NOT_FOUND));
        MerchantOwnerPlace currentOwner = ownerPlaceRepository.findByPlaceIdForUpdate(placeId).orElse(null);
        Long currentOwnerUserId = currentOwner == null ? null : currentOwner.getMerchantOwnerUserId();
        if (!Objects.equals(application.getPreviousOwnerUserId(), currentOwnerUserId)) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        List<MerchantPlaceMember> members = memberRepository.findAllByPlaceId(placeId);
        var invitations = invitationRepository.findAllByPlaceIdAndStatus(placeId, MerchantPlaceInvitationStatus.PENDING);
        List<TouristOffer> offers = currentOwnerUserId == null ? List.of()
                : touristOfferRepository.findAllByMerchantOwnerUserIdAndPlaceIdForUpdate(currentOwnerUserId, placeId);
        ReviewSnapshots snapshots = snapshotSerializer.capture(
                placeId, application.getPreviousOwnerUserId(), currentOwnerUserId, members, invitations, offers
        );
        return new ClaimReviewContext(snapshots);
    }

    private void saveReviewHistory(
            PlaceRegistrationApplication application,
            Long adminUserId,
            PlaceRegistrationStatus beforeStatus,
            long reviewedVersion,
            String reason,
            ReviewSnapshots snapshots,
            LocalDateTime now
    ) {
        reviewHistoryRepository.save(MerchantPlaceApplicationReviewHistory.create(
                application.getId(),
                adminUserId,
                beforeStatus,
                application.getStatus(),
                reviewedVersion,
                reason,
                snapshots.previousOwner(),
                snapshots.team(),
                snapshots.offers(),
                now
        ));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public record DownloadedAttachment(byte[] bytes, String contentType) {
    }

    private record ClaimReviewContext(ReviewSnapshots snapshots) {
    }

}
