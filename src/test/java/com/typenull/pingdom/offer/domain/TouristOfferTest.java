package com.typenull.pingdom.offer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TouristOfferTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 16, 12, 0);

    @Test
    void publishedOfferIssuesCouponsUntilQuantityIsExhausted() {
        TouristOffer offer = offer(1, 7);
        offer.publish(NOW.minusMinutes(1));

        LocalDateTime expiresAt = offer.issueCoupon(NOW);

        assertThat(offer.getIssuedQuantity()).isEqualTo(1);
        assertThat(offer.isSoldOut()).isTrue();
        assertThat(expiresAt).isEqualTo(NOW.plusDays(7));
        assertThatThrownBy(() -> offer.issueCoupon(NOW.plusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void couponExpiryDoesNotExceedOfferEnd() {
        TouristOffer offer = offer(10, 30);
        offer.publish(NOW.minusMinutes(1));

        assertThat(offer.issueCoupon(NOW)).isEqualTo(NOW.plusDays(10));
    }

    @Test
    void draftAndClosedOffersCannotIssueCoupons() {
        TouristOffer offer = offer(10, 7);

        assertThatThrownBy(() -> offer.issueCoupon(NOW)).isInstanceOf(IllegalStateException.class);

        offer.publish(NOW.minusMinutes(1));
        offer.close(NOW);

        assertThatThrownBy(() -> offer.issueCoupon(NOW.plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
    }

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
