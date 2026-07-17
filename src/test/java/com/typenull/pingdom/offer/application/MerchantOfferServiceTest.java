package com.typenull.pingdom.offer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.offer.api.dto.CouponRedeemRequest;
import com.typenull.pingdom.offer.api.dto.OfferCreateRequest;
import com.typenull.pingdom.offer.domain.CouponStatus;
import com.typenull.pingdom.offer.domain.TouristCoupon;
import com.typenull.pingdom.offer.domain.TouristOffer;
import com.typenull.pingdom.offer.domain.exception.OfferErrorCode;
import com.typenull.pingdom.offer.domain.exception.OfferException;
import com.typenull.pingdom.offer.infrastructure.TouristCouponRepository;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantOfferServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 16, 12, 0);
    private static final String CODE = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

    @Mock private TouristOfferRepository offerRepository;
    @Mock private TouristCouponRepository couponRepository;
    @Mock private MerchantOfferAccessPolicy accessPolicy;
    @Mock private Clock clock;

    @InjectMocks private MerchantOfferService offerService;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-07-16T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void owningMerchantCanRedeemCouponOnce() {
        TouristCoupon coupon = TouristCoupon.issue(1L, 2L, CODE, NOW.minusDays(1), NOW.plusDays(1));
        TouristOffer offer = offer();
        when(couponRepository.findByCodeForUpdate(CODE)).thenReturn(Optional.of(coupon));
        when(offerRepository.findByIdAndMerchantOwnerUserId(1L, 10L)).thenReturn(Optional.of(offer));

        var response = offerService.redeem(10L, new CouponRedeemRequest(CODE.toUpperCase()));

        verify(accessPolicy).requireOwnedPlace(10L, 100L, NOW);
        assertThat(response.status()).isEqualTo(CouponStatus.REDEEMED);
        assertThat(coupon.getRedeemedBy()).isEqualTo(10L);
    }

    @Test
    void merchantCannotRedeemCouponForAnotherOwnersOffer() {
        TouristCoupon coupon = TouristCoupon.issue(1L, 2L, CODE, NOW.minusDays(1), NOW.plusDays(1));
        when(couponRepository.findByCodeForUpdate(CODE)).thenReturn(Optional.of(coupon));
        when(offerRepository.findByIdAndMerchantOwnerUserId(1L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> offerService.redeem(10L, new CouponRedeemRequest(CODE)))
                .isInstanceOfSatisfying(OfferException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OfferErrorCode.COUPON_NOT_FOUND));

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ISSUED);
    }

    @Test
    void expiredCouponCannotBeRedeemed() {
        TouristCoupon coupon = TouristCoupon.issue(1L, 2L, CODE, NOW.minusDays(2), NOW.minusDays(1));
        TouristOffer offer = offer();
        when(couponRepository.findByCodeForUpdate(CODE)).thenReturn(Optional.of(coupon));
        when(offerRepository.findByIdAndMerchantOwnerUserId(1L, 10L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> offerService.redeem(10L, new CouponRedeemRequest(CODE)))
                .isInstanceOfSatisfying(OfferException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OfferErrorCode.COUPON_NOT_REDEEMABLE));
    }

    @Test
    void offerWhoseEndTimeHasPassedCannotBeCreated() {
        OfferCreateRequest request = new OfferCreateRequest(
                100L,
                "Offer",
                "설명",
                "혜택",
                NOW.minusDays(2),
                NOW.minusDays(1),
                10,
                1
        );

        assertThatThrownBy(() -> offerService.create(10L, request))
                .isInstanceOfSatisfying(OfferException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OfferErrorCode.INVALID_OFFER_PERIOD));
    }

    private TouristOffer offer() {
        return TouristOffer.draft(
                10L,
                100L,
                "Offer",
                "설명",
                "혜택",
                NOW.minusDays(2),
                NOW.plusDays(2),
                10,
                7,
                NOW.minusDays(3)
        );
    }
}
