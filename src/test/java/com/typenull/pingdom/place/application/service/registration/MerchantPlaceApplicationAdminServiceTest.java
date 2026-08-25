package com.typenull.pingdom.place.application.service.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
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
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.MerchantPlaceApplicationReviewHistoryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationAttachmentRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantPlaceApplicationAdminServiceTest {

    @Mock private PlaceRegistrationApplicationRepository applicationRepository;
    @Mock private PlaceRegistrationAttachmentRepository attachmentRepository;
    @Mock private MapPlaceRepository placeRepository;
    @Mock private PlaceRegistrationService legacyPlaceRegistrationService;
    @Mock private MerchantOwnerProfileRepository profileRepository;
    @Mock private MerchantVerificationRepository verificationRepository;
    @Mock private MerchantOwnerPlaceRepository ownerPlaceRepository;
    @Mock private MerchantPlaceMemberRepository memberRepository;
    @Mock private MerchantPlaceInvitationRepository invitationRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserAccessStatusService userAccessStatusService;
    @Mock private MerchantVerificationCipher verificationCipher;
    @Mock private AdminRoleAuthorizationService authorizationService;
    @Mock private AdminAuditLogService auditLogService;
    @Mock private TouristOfferRepository touristOfferRepository;
    @Mock private MerchantPlaceApplicationReviewHistoryRepository reviewHistoryRepository;
    @Mock private S3ObjectStorage storage;
    @Mock private ObjectMapper objectMapper;
    @Mock private Clock clock;

    @InjectMocks
    private MerchantPlaceApplicationService service;

    @BeforeEach
    void setUpClock() {
        org.mockito.Mockito.lenient().when(clock.instant()).thenReturn(Instant.parse("2026-08-24T00:00:00Z"));
        org.mockito.Mockito.lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void adminDetailDecryptsRegistrationNumberAndRecordsAuditLog() {
        PlaceRegistrationApplication application = application(12L);
        when(application.getEncryptedBusinessRegistrationNumber()).thenReturn("encrypted-number");
        when(applicationRepository.findById(12L)).thenReturn(Optional.of(application));
        when(verificationCipher.decrypt("encrypted-number")).thenReturn("1234567890");
        when(attachmentRepository.findAllByApplicationIdOrderByDocumentTypeAscDisplayOrderAscIdAsc(12L))
                .thenReturn(List.of());

        var response = service.getForAdmin(99L, 12L);

        assertThat(response.businessRegistrationNumber()).isEqualTo("1234567890");
        verify(authorizationService).requirePermission(99L, AdminPermission.MERCHANT_REVIEW);
        verify(auditLogService).record(
                99L,
                AdminAuditAction.MERCHANT_PLACE_APPLICATION_VIEWED,
                AdminAuditTargetType.MERCHANT_PLACE_APPLICATION,
                12L,
                "통합 신청 민감정보 상세 조회",
                Map.of(),
                Map.of()
        );
    }

    @Test
    void attachmentDownloadRequiresRelatedActiveFileAndRecordsAuditLog() {
        PlaceRegistrationApplication application = application(12L);
        PlaceRegistrationAttachment attachment = org.mockito.Mockito.mock(PlaceRegistrationAttachment.class);
        when(applicationRepository.findById(12L)).thenReturn(Optional.of(application));
        when(attachmentRepository.findByIdAndApplicationId(44L, 12L)).thenReturn(Optional.of(attachment));
        when(attachment.isActive()).thenReturn(true);
        when(attachment.isRetentionExpired(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(attachment.getStorageKey()).thenReturn("private/applications/12/license.pdf");
        when(attachment.getContentType()).thenReturn("application/pdf");
        when(attachment.getDocumentType()).thenReturn(
                com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachmentType.BUSINESS_REGISTRATION
        );
        when(attachment.getId()).thenReturn(44L);
        when(storage.getBytes("private/applications/12/license.pdf")).thenReturn(new byte[] {1, 2});
        when(clock.instant()).thenReturn(Instant.parse("2026-08-19T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        var downloaded = service.downloadAttachmentForAdmin(99L, 12L, 44L);

        assertThat(downloaded.bytes()).containsExactly(1, 2);
        assertThat(downloaded.contentType()).isEqualTo("application/pdf");
        verify(auditLogService).record(
                eq(99L),
                eq(AdminAuditAction.MERCHANT_PLACE_APPLICATION_ATTACHMENT_VIEWED),
                eq(AdminAuditTargetType.MERCHANT_PLACE_APPLICATION_ATTACHMENT),
                eq(44L),
                eq("통합 신청 민감 첨부 관리자 열람: BUSINESS_REGISTRATION"),
                eq(Map.of()),
                eq(Map.of("applicationId", 12L))
        );
    }

    @Test
    void attachmentMetadataLookupRecordsAuditLogForEachVisibleAttachment() {
        PlaceRegistrationApplication application = application(12L);
        PlaceRegistrationAttachment attachment = org.mockito.Mockito.mock(PlaceRegistrationAttachment.class);
        when(applicationRepository.findById(12L)).thenReturn(Optional.of(application));
        when(attachmentRepository.findAllByApplicationIdOrderByDocumentTypeAscDisplayOrderAscIdAsc(12L))
                .thenReturn(List.of(attachment));
        when(attachment.isActive()).thenReturn(true);
        when(attachment.isRetentionExpired(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(attachment.getId()).thenReturn(44L);
        when(attachment.getDocumentType()).thenReturn(
                com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachmentType.BUSINESS_REGISTRATION
        );
        when(attachment.getOriginalFilename()).thenReturn("license.pdf");
        when(attachment.getContentType()).thenReturn("application/pdf");
        when(attachment.getFileSize()).thenReturn(10L);
        when(attachment.getUploadedAt()).thenReturn(LocalDateTime.of(2026, 8, 19, 0, 0));
        when(attachment.getDisplayOrder()).thenReturn(0);
        when(clock.instant()).thenReturn(Instant.parse("2026-08-19T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        var attachments = service.listAttachmentsForAdmin(99L, 12L);

        assertThat(attachments).hasSize(1);
        verify(auditLogService).record(
                99L,
                AdminAuditAction.MERCHANT_PLACE_APPLICATION_ATTACHMENT_VIEWED,
                AdminAuditTargetType.MERCHANT_PLACE_APPLICATION_ATTACHMENT,
                44L,
                "통합 신청 민감 첨부 메타데이터 조회: BUSINESS_REGISTRATION",
                Map.of(),
                Map.of("applicationId", 12L)
        );
    }

    @Test
    void submitLocksApplicantBeforeLoadingApplication() {
        PlaceRegistrationApplication application = org.mockito.Mockito.mock(PlaceRegistrationApplication.class);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(applicationRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(application));
        when(application.getApplicantUserId()).thenReturn(1L);

        assertThatThrownBy(() -> service.submit(1L, 12L))
                .isInstanceOf(com.typenull.pingdom.place.domain.exception.PlaceRegistrationException.class);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(userRepository, applicationRepository);
        inOrder.verify(userRepository).findByIdForUpdate(1L);
        inOrder.verify(applicationRepository).findByIdForUpdate(12L);
    }

    @Test
    void reviewRejectsStaleVersionBeforeAnyStateChange() {
        PlaceRegistrationApplication application = org.mockito.Mockito.mock(PlaceRegistrationApplication.class);
        when(application.getApplicationType()).thenReturn(MerchantPlaceApplicationType.NEW_PLACE);
        when(application.getStatus()).thenReturn(PlaceRegistrationStatus.PENDING);
        when(application.matchesVersion(3L)).thenReturn(false);
        when(applicationRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.reject(99L, 12L,
                new com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationReviewRequest(3L, "반려")))
                .isInstanceOf(com.typenull.pingdom.place.domain.exception.PlaceRegistrationException.class)
                .extracting(exception -> ((com.typenull.pingdom.place.domain.exception.PlaceRegistrationException) exception).getErrorCode())
                .isEqualTo(com.typenull.pingdom.place.domain.exception.PlaceRegistrationErrorCode.STALE_REVIEW_VERSION);

        verify(authorizationService).requirePermission(99L, AdminPermission.MERCHANT_REVIEW);
        org.mockito.Mockito.verifyNoInteractions(auditLogService, reviewHistoryRepository);
    }

    @Test
    void submitRejectsAnotherPendingClaimForSamePlaceBeforeStateTransition() {
        PlaceRegistrationApplication application = org.mockito.Mockito.mock(PlaceRegistrationApplication.class);
        com.typenull.pingdom.place.domain.place.core.MapPlace place = org.mockito.Mockito.mock(
                com.typenull.pingdom.place.domain.place.core.MapPlace.class);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(applicationRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(application));
        when(application.getApplicantUserId()).thenReturn(1L);
        when(application.getApplicationType()).thenReturn(MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM);
        when(application.getExistingPlaceId()).thenReturn(30L);
        when(application.getLegalName()).thenReturn("홍길동");
        when(application.getBusinessName()).thenReturn("핑덤");
        when(application.getEncryptedBusinessRegistrationNumber()).thenReturn("encrypted");
        when(application.getMerchantDisplayName()).thenReturn("핑덤");
        when(application.getMerchantContactEmail()).thenReturn("owner@pingdom.test");
        when(application.getMerchantContactPhone()).thenReturn("+821012345678");
        when(placeRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(place));
        when(ownerPlaceRepository.findByPlaceIdForUpdate(30L)).thenReturn(Optional.empty());
        when(applicationRepository.existsByExistingPlaceIdAndApplicationTypeAndStatus(
                30L, MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM, PlaceRegistrationStatus.PENDING
        )).thenReturn(true);

        assertThatThrownBy(() -> service.submit(1L, 12L))
                .isInstanceOf(com.typenull.pingdom.place.domain.exception.PlaceRegistrationException.class)
                .extracting(exception -> ((com.typenull.pingdom.place.domain.exception.PlaceRegistrationException) exception).getErrorCode())
                .isEqualTo(com.typenull.pingdom.place.domain.exception.PlaceRegistrationErrorCode.DUPLICATE_PLACE);

        verify(application, org.mockito.Mockito.never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void approvalRejectsOwnershipChangeBeforeMerchantOrTeamStateChanges() {
        PlaceRegistrationApplication application = application(12L);
        MerchantOwnerPlace currentOwner = org.mockito.Mockito.mock(MerchantOwnerPlace.class);
        com.typenull.pingdom.place.domain.place.core.MapPlace place = org.mockito.Mockito.mock(
                com.typenull.pingdom.place.domain.place.core.MapPlace.class);
        when(application.getApplicationType()).thenReturn(MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM);
        when(application.getStatus()).thenReturn(PlaceRegistrationStatus.PENDING);
        when(application.matchesVersion(4L)).thenReturn(true);
        when(application.getExistingPlaceId()).thenReturn(30L);
        when(application.getPreviousOwnerUserId()).thenReturn(20L);
        when(applicationRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(application));
        when(placeRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(place));
        when(ownerPlaceRepository.findByPlaceIdForUpdate(30L)).thenReturn(Optional.of(currentOwner));
        when(currentOwner.getMerchantOwnerUserId()).thenReturn(21L);

        assertThatThrownBy(() -> service.approve(99L, 12L,
                new com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationReviewRequest(4L, "승인")))
                .isInstanceOf(com.typenull.pingdom.place.domain.exception.PlaceRegistrationException.class)
                .extracting(exception -> ((com.typenull.pingdom.place.domain.exception.PlaceRegistrationException) exception).getErrorCode())
                .isEqualTo(com.typenull.pingdom.place.domain.exception.PlaceRegistrationErrorCode.INVALID_STATE);

        verify(application, org.mockito.Mockito.never()).approve(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verifyNoInteractions(profileRepository, verificationRepository, memberRepository, invitationRepository,
                touristOfferRepository, auditLogService, reviewHistoryRepository);
    }

    @Test
    void approvalCompletesApplicationBeforeClosingPreviousOwnersOffers() throws Exception {
        PlaceRegistrationApplication application = application(12L);
        MerchantOwnerPlace currentOwner = org.mockito.Mockito.mock(MerchantOwnerPlace.class);
        com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile profile = org.mockito.Mockito.mock(
                com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile.class);
        com.typenull.pingdom.identity.domain.merchant.MerchantVerification verification = org.mockito.Mockito.mock(
                com.typenull.pingdom.identity.domain.merchant.MerchantVerification.class);
        com.typenull.pingdom.place.domain.place.core.MapPlace place = org.mockito.Mockito.mock(
                com.typenull.pingdom.place.domain.place.core.MapPlace.class);
        User applicant = org.mockito.Mockito.mock(User.class);

        when(application.getApplicationType()).thenReturn(MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM);
        when(application.getStatus()).thenReturn(PlaceRegistrationStatus.PENDING);
        when(application.matchesVersion(4L)).thenReturn(true);
        when(application.getApplicantUserId()).thenReturn(10L);
        when(application.getExistingPlaceId()).thenReturn(30L);
        when(application.getPreviousOwnerUserId()).thenReturn(20L);
        when(application.getLegalName()).thenReturn("홍길동");
        when(application.getBusinessName()).thenReturn("핑덤");
        when(application.getEncryptedBusinessRegistrationNumber()).thenReturn("encrypted");
        when(application.getMerchantDisplayName()).thenReturn("핑덤");
        when(application.getMerchantContactEmail()).thenReturn("owner@pingdom.test");
        when(application.getMerchantDescription()).thenReturn("소개");
        when(application.getMerchantContactPhone()).thenReturn("+821012345678");
        when(application.getAttachments()).thenReturn(List.of());
        when(applicationRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(application));
        when(placeRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(place));
        when(ownerPlaceRepository.findByPlaceIdForUpdate(30L)).thenReturn(Optional.of(currentOwner));
        when(currentOwner.getMerchantOwnerUserId()).thenReturn(20L);
        when(memberRepository.findAllByPlaceId(30L)).thenReturn(List.of());
        when(invitationRepository.findAllByPlaceIdAndStatus(
                30L, com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitationStatus.PENDING
        )).thenReturn(List.of());
        when(touristOfferRepository.findAllByMerchantOwnerUserIdAndPlaceIdForUpdate(20L, 30L)).thenReturn(List.of());
        when(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any())).thenReturn("{}");
        when(profileRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(profile));
        when(profile.getStatus()).thenReturn(com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus.PENDING);
        when(verificationRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(verification));
        when(verification.getIdentityStatus()).thenReturn(
                com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus.PENDING);
        when(verification.getBusinessStatus()).thenReturn(
                com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus.PENDING);
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(applicant));
        when(memberRepository.findByPlaceIdAndUserIdForUpdate(30L, 10L)).thenReturn(Optional.empty());

        service.approve(99L, 12L,
                new com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationReviewRequest(4L, "승인"));

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(application, touristOfferRepository);
        inOrder.verify(application).complete(eq(30L), org.mockito.ArgumentMatchers.any());
        inOrder.verify(touristOfferRepository).closeAllByMerchantOwnerUserIdAndPlaceIdIn(
                eq(20L), eq(java.util.Set.of(30L)), org.mockito.ArgumentMatchers.any());
        org.mockito.InOrder roleActivationOrder = org.mockito.Mockito.inOrder(applicant, userAccessStatusService);
        roleActivationOrder.verify(applicant).activateMerchantOwnerRole();
        roleActivationOrder.verify(userAccessStatusService).evict(10L);
    }

    private PlaceRegistrationApplication application(Long id) {
        PlaceRegistrationApplication application = org.mockito.Mockito.mock(PlaceRegistrationApplication.class);
        org.mockito.Mockito.lenient().when(application.getId()).thenReturn(id);
        when(application.getApplicationType()).thenReturn(MerchantPlaceApplicationType.NEW_PLACE);
        return application;
    }
}
