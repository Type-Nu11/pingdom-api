package com.typenull.pingdom.offer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TouristCouponTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 16, 12, 0);

    /**
     * 만료 전 쿠폰을 사용하면 REDEEMED 상태와 사용자를 기록하고 재사용에는 IllegalStateException이 발생하는지 검증.
     * 동일 쿠폰의 중복 사용을 방지.
     */
    @Test
    void redeemsUnexpiredCouponOnce() {
        TouristCoupon coupon = coupon();

        coupon.redeem(20L, NOW.plusHours(1));

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.REDEEMED);
        assertThat(coupon.getRedeemedBy()).isEqualTo(20L);
        assertThatThrownBy(() -> coupon.redeem(20L, NOW.plusHours(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 만료일을 지난 쿠폰의 계산 상태가 EXPIRED이며 사용 시 IllegalStateException이 발생하는지 검증.
     */
    @Test
    void rejectsExpiredCouponRedemption() {
        TouristCoupon coupon = coupon();

        assertThat(coupon.statusAt(NOW.plusDays(2))).isEqualTo(CouponStatus.EXPIRED);
        assertThatThrownBy(() -> coupon.redeem(20L, NOW.plusDays(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 사용자 없이 쿠폰을 사용하면 NullPointerException이 발생하고 ISSUED 상태·미사용자·미사용시각이 유지되는지 검증.
     * 검증 실패가 쿠폰을 부분적으로 변경하는 회귀를 방지.
     */
    @Test
    void nullRedeemerPreservesCoupon() {
        TouristCoupon coupon = coupon();

        assertThatThrownBy(() -> coupon.redeem(null, NOW.plusHours(1)))
                .isInstanceOf(NullPointerException.class);

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ISSUED);
        assertThat(coupon.getRedeemedBy()).isNull();
        assertThat(coupon.getRedeemedAt()).isNull();
    }

    /**
     * 고정된 코드와 발급 시각을 가진 하루 유효 쿠폰을 생성해 사용·만료 경계를 재현.
     */
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
