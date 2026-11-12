package com.typenull.pingdom.identity.domain.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class MerchantPlaceInvitationTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 8, 24, 0, 0);

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void expiresPendingInvitationAtOrAfterExpiration(int secondsAfterExpiration) {
        MerchantPlaceInvitation invitation = pendingInvitation();

        assertThat(invitation.expireIfPending(EXPIRES_AT.plusSeconds(secondsAfterExpiration))).isTrue();

        assertThat(invitation.getStatus()).isEqualTo(MerchantPlaceInvitationStatus.EXPIRED);
        assertThat(invitation.getRespondedAt()).isNull();
        assertThat(invitation.getExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void keepsPendingInvitationBeforeExpiration() {
        MerchantPlaceInvitation invitation = pendingInvitation();

        assertThat(invitation.expireIfPending(EXPIRES_AT.minusNanos(1))).isFalse();

        assertThat(invitation.getStatus()).isEqualTo(MerchantPlaceInvitationStatus.PENDING);
        assertThat(invitation.getRespondedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = MerchantPlaceInvitationStatus.class, names = {"ACCEPTED", "REVOKED", "EXPIRED"})
    void doesNotChangeTerminalInvitation(MerchantPlaceInvitationStatus status) {
        LocalDateTime respondedAt = status == MerchantPlaceInvitationStatus.EXPIRED
                ? null : EXPIRES_AT.minusHours(1);
        MerchantPlaceInvitation invitation = MerchantPlaceInvitation.builder()
                .status(status).expiresAt(EXPIRES_AT).respondedAt(respondedAt).build();

        assertThat(invitation.expireIfPending(EXPIRES_AT.plusDays(1))).isFalse();

        assertThat(invitation.getStatus()).isEqualTo(status);
        assertThat(invitation.getRespondedAt()).isEqualTo(respondedAt);
    }

    private MerchantPlaceInvitation pendingInvitation() {
        return MerchantPlaceInvitation.pending(
                1L, 10L, 20L, MerchantPlaceMemberRole.STAFF, EXPIRES_AT, EXPIRES_AT.minusDays(1));
    }
}
