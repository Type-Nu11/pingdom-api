package com.typenull.pingdom.offer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TouristCouponTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 16, 12, 0);

    @Test
    void issuedCouponCanBeRedeemedOnceBeforeExpiry() {
        TouristCoupon coupon = coupon();

        coupon.redeem(20L, NOW.plusHours(1));

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.REDEEMED);
        assertThat(coupon.getRedeemedBy()).isEqualTo(20L);
        assertThatThrownBy(() -> coupon.redeem(20L, NOW.plusHours(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiredCouponIsReportedAsExpiredAndCannotBeRedeemed() {
        TouristCoupon coupon = coupon();

        assertThat(coupon.statusAt(NOW.plusDays(2))).isEqualTo(CouponStatus.EXPIRED);
        assertThatThrownBy(() -> coupon.redeem(20L, NOW.plusDays(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    private TouristCoupon coupon() {
        return TouristCoupon.issue(
                1L,
                2L,
                "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                NOW,
                NOW.plusDays(1)
        );
    }
}
