package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantVerificationReviewRequest;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.infrastructure.crypto.MerchantVerificationCipher;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class MerchantVerificationAdminServiceTest {

    @Mock private MerchantVerificationRepository verificationRepository;
    @Mock private MerchantOwnerProfileRepository profileRepository;
    @Mock private MerchantVerificationCipher verificationCipher;
    @Mock private AdminAuditLogService auditLogService;
    @Mock private TouristOfferRepository touristOfferRepository;
    @Mock private Clock clock;

    @InjectMocks
    private MerchantVerificationAdminService adminService;

    @Test
    void manualReviewRecordsBothResultsAndAuditLog() {
        Long adminUserId = 99L;
        Long userId = 1L;
        MerchantOwnerProfile profile = profile(userId);
        MerchantVerification verification = MerchantVerification.pending(
                userId,
                "김핑덤",
                "핑덤 카페",
                "encrypted-number",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
        when(profileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        when(verificationRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(verification));
        when(verificationCipher.decrypt("encrypted-number")).thenReturn("1234567890");
        when(clock.instant()).thenReturn(Instant.parse("2026-07-15T03:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        var response = adminService.review(
                adminUserId,
                userId,
                new MerchantVerificationReviewRequest(true, true, " 확인 완료 ")
        );

        assertThat(response.identityStatus()).isEqualTo(MerchantVerificationStatus.APPROVED);
        assertThat(response.businessStatus()).isEqualTo(MerchantVerificationStatus.APPROVED);
        verify(auditLogService).record(
                eq(adminUserId),
                eq(AdminAuditAction.MERCHANT_VERIFICATION_REVIEWED),
                eq(AdminAuditTargetType.MERCHANT_VERIFICATION),
                eq(userId),
                eq("확인 완료"),
                eq(Map.of(
                        "identityStatus", MerchantVerificationStatus.PENDING,
                        "businessStatus", MerchantVerificationStatus.PENDING
                )),
                eq(Map.of(
                        "identityStatus", MerchantVerificationStatus.APPROVED,
                        "businessStatus", MerchantVerificationStatus.APPROVED
                ))
        );
    }

    @Test
    void rejectedReviewClosesMerchantOffers() {
        Long adminUserId = 99L;
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 3, 0);
        MerchantOwnerProfile profile = profile(userId);
        MerchantVerification verification = MerchantVerification.pending(
                userId,
                "김핑덤",
                "핑덤 카페",
                "encrypted-number",
                now.minusDays(1)
        );
        when(profileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        when(verificationRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(verification));
        when(verificationCipher.decrypt("encrypted-number")).thenReturn("1234567890");
        when(clock.instant()).thenReturn(Instant.parse("2026-07-15T03:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        adminService.review(
                adminUserId,
                userId,
                new MerchantVerificationReviewRequest(true, false, "사업자 정보 불일치")
        );

        verify(touristOfferRepository).closeAllByMerchantOwnerUserId(userId, now);
    }

    @Test
    void listMasksBusinessRegistrationNumber() {
        MerchantVerification verification = MerchantVerification.pending(
                1L,
                "김핑덤",
                "핑덤 카페",
                "encrypted-number",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
        when(verificationRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(verification)));
        when(verificationCipher.decrypt("encrypted-number")).thenReturn("1234567890");

        var response = adminService.list(null, null, 1, 20);

        assertThat(response.verifications()).singleElement().satisfies(item ->
                assertThat(item.maskedBusinessRegistrationNumber()).isEqualTo("123-45-*****")
        );
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(verificationRepository).findAll(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void detailViewRecordsSensitiveInformationAccess() {
        Long adminUserId = 99L;
        Long userId = 1L;
        MerchantVerification verification = MerchantVerification.pending(
                userId,
                "김핑덤",
                "핑덤 카페",
                "encrypted-number",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
        when(verificationRepository.findById(userId)).thenReturn(Optional.of(verification));
        when(verificationCipher.decrypt("encrypted-number")).thenReturn("1234567890");

        var response = adminService.get(adminUserId, userId);

        assertThat(response.businessRegistrationNumber()).isEqualTo("1234567890");
        verify(auditLogService).record(
                adminUserId,
                AdminAuditAction.MERCHANT_VERIFICATION_VIEWED,
                AdminAuditTargetType.MERCHANT_VERIFICATION,
                userId,
                "Merchant 검증 민감정보 상세 조회",
                Map.of(),
                Map.of()
        );
    }

    private MerchantOwnerProfile profile(Long userId) {
        return MerchantOwnerProfile.pending(
                userId,
                "핑덤 카페",
                "핑덤 사장님",
                null,
                "owner@example.com",
                "010-1111-2222",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
    }
}
