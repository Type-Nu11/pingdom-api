package com.typenull.pingdom.place.application.service.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
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
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationAttachmentRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    @Mock private MerchantVerificationCipher verificationCipher;
    @Mock private AdminRoleAuthorizationService authorizationService;
    @Mock private AdminAuditLogService auditLogService;
    @Mock private TouristOfferRepository touristOfferRepository;
    @Mock private S3ObjectStorage storage;
    @Mock private Clock clock;

    @InjectMocks
    private MerchantPlaceApplicationService service;

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

    private PlaceRegistrationApplication application(Long id) {
        PlaceRegistrationApplication application = org.mockito.Mockito.mock(PlaceRegistrationApplication.class);
        when(application.getId()).thenReturn(id);
        when(application.getApplicationType()).thenReturn(MerchantPlaceApplicationType.NEW_PLACE);
        return application;
    }
}
