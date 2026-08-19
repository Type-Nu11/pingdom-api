package com.typenull.pingdom.place.application.service.registration;

import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitationStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceInvitationRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMemberRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.infrastructure.crypto.MerchantVerificationCipher;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationAttachmentResponse;
import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationListItemResponse;
import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationPageResponse;
import com.typenull.pingdom.place.api.dto.registration.AdminMerchantPlaceApplicationResponse;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationPageResponse;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationRequest;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationResponse;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationReviewRequest;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationAttachmentRequest;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationRequest;
import com.typenull.pingdom.place.domain.exception.PlaceRegistrationErrorCode;
import com.typenull.pingdom.place.domain.exception.PlaceRegistrationException;
import com.typenull.pingdom.place.domain.place.category.PlaceCategoryPolicy;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationCategory;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationAttachmentRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Web의 사업자 검증과 장소 등록/소유권 신청을 단일 심사 단위로 관리합니다.
 * 기존 장소 등록 서비스는 신규 장소 생성과 운영시간 반영 책임을 계속 보유합니다.
 */
@Service
@RequiredArgsConstructor
public class MerchantPlaceApplicationService {

    private final PlaceRegistrationApplicationRepository applicationRepository;
    private final PlaceRegistrationAttachmentRepository attachmentRepository;
    private final MapPlaceRepository placeRepository;
    private final PlaceRegistrationService legacyPlaceRegistrationService;
    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantVerificationRepository verificationRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;
    private final MerchantPlaceMemberRepository memberRepository;
    private final MerchantPlaceInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final MerchantVerificationCipher verificationCipher;
    private final AdminRoleAuthorizationService authorizationService;
    private final AdminAuditLogService auditLogService;
    private final TouristOfferRepository touristOfferRepository;
    private final S3ObjectStorage storage;
    private final Clock clock;

    @Transactional
    public MerchantPlaceApplicationResponse create(Long userId, MerchantPlaceApplicationRequest request) {
        PlaceRegistrationApplication application;
        if (request.applicationType() == MerchantPlaceApplicationType.NEW_PLACE) {
            PlaceRegistrationRequest newPlace = requireNewPlace(request);
            Long id = legacyPlaceRegistrationService.create(userId, newPlace).id();
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
        return page(applicationRepository.findAllByApplicantUserIdAndApplicationTypeNot(
                userId, MerchantPlaceApplicationType.LEGACY, pageable(page, limit)));
    }

    @Transactional(readOnly = true)
    public MerchantPlaceApplicationPageResponse listAll(int page, int limit) {
        return page(applicationRepository.findAllByApplicationTypeNot(MerchantPlaceApplicationType.LEGACY, pageable(page, limit)));
    }

    @Transactional(readOnly = true)
    public AdminMerchantPlaceApplicationPageResponse listForAdmin(Long adminUserId, int page, int limit) {
        authorizationService.requirePermission(adminUserId, AdminPermission.MERCHANT_REVIEW);
        Page<PlaceRegistrationApplication> result = applicationRepository.findAllByApplicationTypeNot(
                MerchantPlaceApplicationType.LEGACY,
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

    @Transactional(readOnly = true)
    public MerchantPlaceApplicationResponse get(Long userId, Long id) {
        PlaceRegistrationApplication application = applicationRepository.findByIdAndApplicantUserId(id, userId)
                .orElseThrow(this::notFound);
        requireUnified(application);
        return response(application);
    }

    @Transactional(readOnly = true)
    public MerchantPlaceApplicationResponse getAny(Long id) {
        PlaceRegistrationApplication application = applicationRepository.findById(id).orElseThrow(this::notFound);
        requireUnified(application);
        return response(application);
    }

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
        return AdminMerchantPlaceApplicationResponse.from(
                application,
                decryptRegistrationNumber(application),
                attachments(application)
        );
    }

    @Transactional(readOnly = true)
    public List<AdminMerchantPlaceApplicationAttachmentResponse> listAttachmentsForAdmin(Long adminUserId, Long id) {
        authorizationService.requirePermission(adminUserId, AdminPermission.MERCHANT_REVIEW);
        return attachments(unified(id));
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

    @Transactional
    public MerchantPlaceApplicationResponse update(Long userId, Long id, MerchantPlaceApplicationRequest request) {
        PlaceRegistrationApplication application = mine(userId, id);
        try {
            if (application.getApplicationType() != request.applicationType()) {
                throw new IllegalStateException("신청 유형은 초안 생성 후 변경할 수 없습니다.");
            }
            if (request.applicationType() == MerchantPlaceApplicationType.NEW_PLACE) {
                legacyPlaceRegistrationService.updateForUnifiedApplication(userId, id, requireNewPlace(request));
            } else {
                refreshClaimSnapshot(application, request, now());
                applyClaimAttachments(application, userId, request.attachments(), now());
            }
            applyMerchantData(application, request, now());
        } catch (IllegalArgumentException exception) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_ATTACHMENT_METADATA);
        } catch (IllegalStateException exception) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        return response(application);
    }

    @Transactional
    public MerchantPlaceApplicationResponse submit(Long userId, Long id) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new PlaceRegistrationException(PlaceRegistrationErrorCode.ACCESS_DENIED));
        PlaceRegistrationApplication application = mine(userId, id);
        requireMerchantData(application);
        if (application.getApplicationType() == MerchantPlaceApplicationType.NEW_PLACE) {
            legacyPlaceRegistrationService.submitForUnifiedApplication(userId, id);
        } else {
            try {
                application.submit(now());
            } catch (IllegalStateException exception) {
                throw new PlaceRegistrationException(application.hasRequiredFiles()
                        ? PlaceRegistrationErrorCode.INVALID_STATE : PlaceRegistrationErrorCode.REQUIRED_FILES_MISSING);
            }
        }
        return response(application);
    }

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

    /** 심사 승인과 사업자 활성화, 장소 생성 또는 소유권 이전을 하나의 트랜잭션에서 완료합니다. */
    @Transactional
    public MerchantPlaceApplicationResponse approve(Long adminUserId, Long id, MerchantPlaceApplicationReviewRequest request) {
        authorizationService.requirePermission(adminUserId, AdminPermission.MERCHANT_REVIEW);
        PlaceRegistrationApplication application = locked(id);
        requireUnified(application);
        if (application.getStatus() != PlaceRegistrationStatus.PENDING) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        LocalDateTime now = now();
        try {
            application.approve(adminUserId, normalizedReason(request), now);
            prepareMerchantVerification(application, now);
            if (application.getApplicationType() == MerchantPlaceApplicationType.NEW_PLACE) {
                Long placeId = legacyPlaceRegistrationService.createApprovedPlaceForUnifiedApplication(
                        application.getApplicantUserId(), application.getId());
                application.complete(placeId, now);
            } else {
                activateMerchant(application, adminUserId, now);
                transferExistingPlace(application, now);
                application.complete(application.getExistingPlaceId(), now);
            }
            approveVerification(application, adminUserId, now);
        } catch (IllegalStateException exception) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        audit(adminUserId, application, AdminAuditAction.MERCHANT_PLACE_APPLICATION_APPROVED, normalizedReason(request));
        return response(application);
    }

    @Transactional
    public MerchantPlaceApplicationResponse reject(Long adminUserId, Long id, MerchantPlaceApplicationReviewRequest request) {
        authorizationService.requirePermission(adminUserId, AdminPermission.MERCHANT_REVIEW);
        PlaceRegistrationApplication application = locked(id);
        requireUnified(application);
        try {
            application.reject(adminUserId, normalizedReason(request), now());
        } catch (IllegalStateException exception) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        audit(adminUserId, application, AdminAuditAction.MERCHANT_PLACE_APPLICATION_REJECTED, normalizedReason(request));
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
        applyClaimAttachments(application, userId, request.attachments(), now);
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

    private void applyClaimAttachments(PlaceRegistrationApplication application, Long userId,
                                       List<PlaceRegistrationAttachmentRequest> requests, LocalDateTime now) {
        List<PlaceRegistrationAttachment> attachments = requests == null ? List.of() : requests.stream()
                .map(request -> PlaceRegistrationAttachment.create(application, request.fileId(), request.documentType(),
                        request.storageKey(), request.originalFilename(), request.contentType(), request.fileSize(),
                        request.fileHash(), userId, now,
                        request.retentionDays() == null ? null : now.plusDays(request.retentionDays()),
                        request.resolvedDisplayOrder()))
                .toList();
        application.replaceAttachments(attachments, now);
    }

    private void prepareMerchantVerification(PlaceRegistrationApplication application, LocalDateTime now) {
        Long userId = application.getApplicantUserId();
        MerchantOwnerProfile profile = profileRepository.findByUserIdForUpdate(userId).orElse(null);
        if (profile == null) {
            profileRepository.save(MerchantOwnerProfile.pending(userId, application.getBusinessName(), application.getMerchantDisplayName(),
                    application.getMerchantDescription(), application.getMerchantContactEmail(), application.getMerchantContactPhone(), now));
        } else if (profile.getStatus() == MerchantOwnerStatus.PENDING) {
            profile.update(application.getBusinessName(), application.getMerchantDisplayName(), application.getMerchantDescription(),
                    application.getMerchantContactEmail(), application.getMerchantContactPhone(), now);
        } else if (profile.getStatus() == MerchantOwnerStatus.REJECTED || profile.getStatus() == MerchantOwnerStatus.REVOKED) {
            profile.reapply(application.getBusinessName(), application.getMerchantDisplayName(), application.getMerchantDescription(),
                    application.getMerchantContactEmail(), application.getMerchantContactPhone(), now);
        } else if (!profile.getBusinessName().equals(application.getBusinessName())) {
            throw new IllegalStateException("활성 사업자의 상호 변경은 기존 사업자 정보 변경 절차를 사용해야 합니다.");
        } else {
            profile.update(application.getBusinessName(), application.getMerchantDisplayName(), application.getMerchantDescription(),
                    application.getMerchantContactEmail(), application.getMerchantContactPhone(), now);
        }

        MerchantVerification verification = verificationRepository.findByUserIdForUpdate(userId).orElse(null);
        if (verification == null) {
            verificationRepository.save(MerchantVerification.pending(userId, application.getLegalName(), application.getBusinessName(),
                    application.getEncryptedBusinessRegistrationNumber(), now));
        } else if (verification.getIdentityStatus() == MerchantVerificationStatus.PENDING
                && verification.getBusinessStatus() == MerchantVerificationStatus.PENDING) {
            verification.update(application.getLegalName(), application.getBusinessName(),
                    application.getEncryptedBusinessRegistrationNumber(), now);
        } else if (verification.getIdentityStatus() == MerchantVerificationStatus.REJECTED
                || verification.getBusinessStatus() == MerchantVerificationStatus.REJECTED) {
            verification.reapply(application.getLegalName(), application.getBusinessName(),
                    application.getEncryptedBusinessRegistrationNumber(), now);
        } else {
            verification.resubmit(application.getLegalName(), application.getBusinessName(),
                    application.getEncryptedBusinessRegistrationNumber(), now);
        }
    }

    private void activateMerchant(PlaceRegistrationApplication application, Long adminUserId, LocalDateTime now) {
        MerchantOwnerProfile profile = profileRepository.findByUserIdForUpdate(application.getApplicantUserId())
                .orElseThrow(() -> new PlaceRegistrationException(PlaceRegistrationErrorCode.MERCHANT_PROFILE_REQUIRED));
        User user = userRepository.findByIdForUpdate(application.getApplicantUserId())
                .orElseThrow(() -> new PlaceRegistrationException(PlaceRegistrationErrorCode.ACCESS_DENIED));
        profile.approve(adminUserId, now);
        user.activateMerchantOwnerRole();
    }

    private void approveVerification(PlaceRegistrationApplication application, Long adminUserId, LocalDateTime now) {
        MerchantVerification verification = verificationRepository.findByUserIdForUpdate(application.getApplicantUserId())
                .orElseThrow(() -> new PlaceRegistrationException(PlaceRegistrationErrorCode.MERCHANT_PROFILE_REQUIRED));
        verification.review(adminUserId, true, true, application.getReviewReason(), now);
    }

    private void transferExistingPlace(PlaceRegistrationApplication application, LocalDateTime now) {
        Long placeId = application.getExistingPlaceId();
        placeRepository.findByIdForUpdate(placeId).orElseThrow(() -> new PlaceRegistrationException(PlaceRegistrationErrorCode.APPLICATION_NOT_FOUND));
        MerchantOwnerPlace currentOwner = ownerPlaceRepository.findByPlaceIdForUpdate(placeId).orElse(null);
        Long ownerId = currentOwner == null ? null : currentOwner.getMerchantOwnerUserId();
        if (!java.util.Objects.equals(application.getPreviousOwnerUserId(), ownerId)) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        if (currentOwner == null) {
            ownerPlaceRepository.save(MerchantOwnerPlace.builder().placeId(placeId)
                    .merchantOwnerUserId(application.getApplicantUserId()).createdAt(now).build());
            ensureOwnerMember(placeId, application.getApplicantUserId(), now);
        } else {
            synchronizePlaceTeam(placeId, currentOwner.getMerchantOwnerUserId(), application.getApplicantUserId(), now);
            currentOwner.transferOwnership(application.getApplicantUserId());
            touristOfferRepository.closeAllByMerchantOwnerUserIdAndPlaceIdIn(ownerId, Set.of(placeId), now);
        }
    }

    private void synchronizePlaceTeam(Long placeId, Long previousOwnerUserId, Long newOwnerUserId, LocalDateTime now) {
        for (MerchantPlaceMember member : memberRepository.findAllByPlaceId(placeId)) {
            if (!member.getUserId().equals(newOwnerUserId) && member.getStatus() == MerchantPlaceMemberStatus.ACTIVE) {
                member.revoke(now);
            }
        }
        invitationRepository.findAllByPlaceIdAndStatus(placeId, MerchantPlaceInvitationStatus.PENDING)
                .forEach(invitation -> invitation.revoke(now));
        ensureOwnerMember(placeId, newOwnerUserId, now);
    }

    private void ensureOwnerMember(Long placeId, Long userId, LocalDateTime now) {
        MerchantPlaceMember owner = memberRepository.findByPlaceIdAndUserIdForUpdate(placeId, userId)
                .orElseGet(() -> MerchantPlaceMember.owner(placeId, userId, now));
        if (owner.getStatus() == MerchantPlaceMemberStatus.REVOKED) {
            owner.restoreAsOwner(userId, now);
        } else if (owner.getRole() != MerchantPlaceMemberRole.OWNER) {
            owner.promoteToOwner(now);
        }
        memberRepository.save(owner);
    }

    private void requireMerchantData(PlaceRegistrationApplication application) {
        if (application.getLegalName() == null || application.getBusinessName() == null
                || application.getEncryptedBusinessRegistrationNumber() == null || application.getMerchantDisplayName() == null
                || application.getMerchantContactEmail() == null || application.getMerchantContactPhone() == null) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
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
        requireUnified(application);
        if (!application.getApplicantUserId().equals(userId)) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.ACCESS_DENIED);
        }
        return application;
    }

    private PlaceRegistrationApplication unified(Long id) {
        PlaceRegistrationApplication application = applicationRepository.findById(id).orElseThrow(this::notFound);
        requireUnified(application);
        return application;
    }

    private PlaceRegistrationApplication locked(Long id) {
        return applicationRepository.findByIdForUpdate(id).orElseThrow(this::notFound);
    }

    private void requireUnified(PlaceRegistrationApplication application) {
        if (application.getApplicationType() == MerchantPlaceApplicationType.LEGACY) {
            throw notFound();
        }
    }

    private PlaceRegistrationException notFound() {
        return new PlaceRegistrationException(PlaceRegistrationErrorCode.APPLICATION_NOT_FOUND);
    }

    private void audit(Long adminUserId, PlaceRegistrationApplication application, AdminAuditAction action, String reason) {
        auditLogService.record(adminUserId, action, AdminAuditTargetType.MERCHANT_PLACE_APPLICATION, application.getId(), reason,
                Map.of("status", PlaceRegistrationStatus.PENDING),
                Map.of("status", application.getStatus(), "applicationType", application.getApplicationType(),
                        "placeId", String.valueOf(application.getCompletedPlaceId() == null
                                ? application.getRegisteredPlaceId() : application.getCompletedPlaceId())));
    }

    private MerchantPlaceApplicationResponse response(PlaceRegistrationApplication application) {
        return MerchantPlaceApplicationResponse.from(application);
    }

    private List<AdminMerchantPlaceApplicationAttachmentResponse> attachments(PlaceRegistrationApplication application) {
        return attachmentRepository.findAllByApplicationIdOrderByDocumentTypeAscDisplayOrderAscIdAsc(application.getId())
                .stream()
                .filter(PlaceRegistrationAttachment::isActive)
                .filter(attachment -> !attachment.isRetentionExpired(now()))
                .map(AdminMerchantPlaceApplicationAttachmentResponse::from)
                .toList();
    }

    private String decryptRegistrationNumber(PlaceRegistrationApplication application) {
        return verificationCipher.decrypt(application.getEncryptedBusinessRegistrationNumber());
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

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public record DownloadedAttachment(byte[] bytes, String contentType) {
    }
}
