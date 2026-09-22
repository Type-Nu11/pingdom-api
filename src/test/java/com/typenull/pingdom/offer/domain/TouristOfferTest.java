package com.typenull.pingdom.offer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TouristOfferTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 16, 12, 0);

    /**
     * 수량 1인 게시 Offer를 발급하면 발급 수 1·품절 상태·7일 만료가 반영되고 추가 발급이 거절되는지 검증한다.
     */
    @Test
    void exhaustsLimitedCouponInventory() {
        TouristOffer offer = offer(1, 7);
        offer.publish(NOW.minusMinutes(1));

        LocalDateTime expiresAt = offer.issueCoupon(NOW);

        assertThat(offer.getIssuedQuantity()).isEqualTo(1);
        assertThat(offer.isSoldOut()).isTrue();
        assertThat(expiresAt).isEqualTo(NOW.plusDays(7));
        assertThatThrownBy(() -> offer.issueCoupon(NOW.plusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 쿠폰 유효기간이 Offer 종료보다 길면 발급 만료일이 Offer 종료일로 제한되는지 검증한다.
     */
    @Test
    void capsExpiryAtOfferEnd() {
        TouristOffer offer = offer(10, 30);
        offer.publish(NOW.minusMinutes(1));

        assertThat(offer.issueCoupon(NOW)).isEqualTo(NOW.plusDays(10));
    }

    /**
     * 초안 상태와 게시 후 종료 상태에서 각각 발급을 시도하면 IllegalStateException이 발생하는지 검증한다.
     */
    @Test
    void rejectsInactiveOfferIssuance() {
        TouristOffer offer = offer(10, 7);

        assertThatThrownBy(() -> offer.issueCoupon(NOW)).isInstanceOf(IllegalStateException.class);

        offer.publish(NOW.minusMinutes(1));
        offer.close(NOW);

        assertThatThrownBy(() -> offer.issueCoupon(NOW.plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * UNLIMITED Offer 발급 후 7일 만료와 무제한 정책이 유지되고 품절이 되지 않는지 검증한다.
     */
    @Test
    void unlimitedInventoryAvoidsSoldOut() {
        TouristOffer offer = TouristOffer.draft(
                1L, 10L, "무제한 Offer", "설명", "혜택",
                NOW.minusHours(1), NOW.plusDays(10), null, 7,
                CouponEligibilityPolicy.PUBLIC,
                CouponInventoryPolicy.UNLIMITED,
                CouponExpiryPolicy.ISSUE_PLUS_DAYS,
                NOW.minusDays(1)
        );
        offer.publish(NOW.minusMinutes(1));

        assertThat(offer.issueCoupon(NOW)).isEqualTo(NOW.plusDays(7));
        assertThat(offer.isSoldOut()).isFalse();
        assertThat(offer.getInventoryPolicy()).isEqualTo(CouponInventoryPolicy.UNLIMITED);
    }

    /**
     * OFFER_END 정책에서는 유효기간 7일과 무관하게 10일 뒤 Offer 종료 시각을 쿠폰 만료로 반환하는지 검증한다.
     */
    @Test
    void usesOfferEndExpiry() {
        TouristOffer offer = TouristOffer.draft(
                1L, 10L, "종료일 만료 Offer", "설명", "혜택",
                NOW.minusHours(1), NOW.plusDays(10), 1, 7,
                CouponEligibilityPolicy.ACTIVE_TRAVEL_SCHEDULE,
                CouponInventoryPolicy.LIMITED,
                CouponExpiryPolicy.OFFER_END,
                NOW.minusDays(1)
        );
        offer.publish(NOW.minusMinutes(1));

        assertThat(offer.issueCoupon(NOW)).isEqualTo(NOW.plusDays(10));
    }

    /**
     * 종료 시각을 지난 초안을 게시하면 IllegalStateException이 발생하는지 검증한다.
     */
    @Test
    void endedOfferCannotBePublished() {
        TouristOffer offer = TouristOffer.draft(
                1L,
                10L,
                "Offer",
                "설명",
                "혜택",
                NOW.minusDays(2),
                NOW.minusDays(1),
                10,
                7,
                NOW.minusDays(3)
        );

        assertThatThrownBy(() -> offer.publish(NOW)).isInstanceOf(IllegalStateException.class);
    }

    /**
     * 현재 발급 기간 안에 있으며 10일 뒤 종료되는 초안 Offer를 주어진 수량·쿠폰 유효기간으로 생성한다.
     */
    private TouristOffer offer(int quantity, int validityDays) {
        return TouristOffer.draft(
                1L,
                10L,
                "관광객 Offer",
                "관광객 전용 설명",
                "음료 무료",
                NOW.minusHours(1),
                NOW.plusDays(10),
                quantity,
                validityDays,
                NOW.minusDays(1)
        );
    }
}
