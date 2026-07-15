package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantVerificationRequest;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.infrastructure.crypto.MerchantVerificationCipher;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantVerificationServiceTest {

    @Mock private MerchantOwnerProfileRepository profileRepository;
    @Mock private MerchantVerificationRepository verificationRepository;
    @Mock private MerchantVerificationCipher verificationCipher;
    @Mock private Clock clock;

    @InjectMocks
    private MerchantVerificationService verificationService;

    @Test
    void merchantOwnerProfileIsRequiredBeforeVerificationApplication() {
        Long userId = 1L;
        when(profileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verificationService.apply(userId, request()))
                .isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MerchantOwnerErrorCode.PROFILE_NOT_FOUND)
                );
    }

    @Test
    void rejectedVerificationCanBeSubmittedAgain() {
        Long userId = 1L;
        MerchantOwnerProfile profile = MerchantOwnerProfile.pending(
                userId,
                "핑덤 카페",
                "핑덤 사장님",
                null,
                "owner@example.com",
                "010-1111-2222",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
        MerchantVerification verification = MerchantVerification.pending(
                userId,
                "기존 이름",
                "핑덤 카페",
                "encrypted-old",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
        verification.review(99L, false, false, "불일치", LocalDateTime.of(2026, 7, 14, 13, 0));
        when(profileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        when(verificationRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(verification));
        when(clock.instant()).thenReturn(Instant.parse("2026-07-15T03:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(verificationCipher.encrypt("1234567890")).thenReturn("encrypted-new");
        when(verificationCipher.decrypt("encrypted-new")).thenReturn("1234567890");

        var response = verificationService.apply(userId, request());

        assertThat(response.legalName()).isEqualTo("김핑덤");
        assertThat(response.businessName()).isEqualTo("핑덤 카페");
        assertThat(response.maskedBusinessRegistrationNumber()).isEqualTo("123-45-*****");
    }

    private MerchantVerificationRequest request() {
        return new MerchantVerificationRequest(" 김핑덤 ", "123-45-67890");
    }
}
