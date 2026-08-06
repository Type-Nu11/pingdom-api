package com.typenull.pingdom.offer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.offer.domain.OfferStatus;
import com.typenull.pingdom.offer.domain.CouponEligibilityPolicy;
import com.typenull.pingdom.offer.domain.CouponExpiryPolicy;
import com.typenull.pingdom.offer.domain.CouponInventoryPolicy;
import com.typenull.pingdom.offer.domain.TouristCoupon;
import com.typenull.pingdom.offer.domain.TouristOffer;
import com.typenull.pingdom.offer.domain.exception.OfferErrorCode;
import com.typenull.pingdom.offer.domain.exception.OfferException;
import com.typenull.pingdom.offer.infrastructure.TouristCouponRepository;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import com.typenull.pingdom.place.application.service.conversion.PlaceConversionEventService;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class TouristOfferServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 16, 12, 0);

    @Mock private TouristOfferRepository offerRepository;
    @Mock private TouristCouponRepository couponRepository;
    @Mock private TouristEligibilityPolicy eligibilityPolicy;
    @Mock private MerchantOfferAccessPolicy merchantAccessPolicy;
    @Mock private PlaceConversionEventService conversionEventService;
    @Mock private Clock clock;

    @InjectMocks private TouristOfferService offerService;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-07-16T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(merchantAccessPolicy.isActiveOwnerOfPlace(10L, 100L, NOW)).thenReturn(true);
    }

    @Test
    void eligibleTouristCanIssueOneCoupon() {
        TouristOffer offer = publishedOffer(2);
        when(offerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(couponRepository.existsByOfferIdAndUserId(1L, 2L)).thenReturn(false);
        when(couponRepository.saveAndFlush(any(TouristCoupon.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = offerService.issue(2L, 1L);

        verify(eligibilityPolicy).requireEligible(2L, NOW, CouponEligibilityPolicy.ACTIVE_TRAVEL_SCHEDULE);
        assertThat(response.offerId()).isEqualTo(1L);
        assertThat(response.status().name()).isEqualTo("ISSUED");
        assertThat(offer.getIssuedQuantity()).isEqualTo(1);
    }

    @Test
    void publicOfferUsesPublicEligibilityPolicyWhenIssuing() {
        TouristOffer offer = TouristOffer.draft(
                10L,
                100L,
                "공개 Offer",
                "설명",
                "혜택",
                NOW.minusHours(1),
                NOW.plusDays(10),
                null,
                7,
                CouponEligibilityPolicy.PUBLIC,
                CouponInventoryPolicy.UNLIMITED,
                CouponExpiryPolicy.OFFER_END,
                NOW.minusHours(2)
        );
        offer.publish(NOW.minusMinutes(1));
        when(offerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(couponRepository.existsByOfferIdAndUserId(1L, 2L)).thenReturn(false);
        when(couponRepository.saveAndFlush(any(TouristCoupon.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = offerService.issue(2L, 1L);

        verify(eligibilityPolicy).requireEligible(2L, NOW, CouponEligibilityPolicy.PUBLIC);
        assertThat(response.expiresAt()).isEqualTo(offer.getEndsAt());
    }

    @Test
    void duplicateCouponIsRejectedBeforeQuantityChanges() {
        TouristOffer offer = publishedOffer(2);
        when(offerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(couponRepository.existsByOfferIdAndUserId(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> offerService.issue(2L, 1L))
                .isInstanceOfSatisfying(OfferException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OfferErrorCode.COUPON_ALREADY_ISSUED));

        assertThat(offer.getIssuedQuantity()).isZero();
        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateCouponConstraintViolationIsMappedToDomainError() {
        TouristOffer offer = publishedOffer(2);
        when(offerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(couponRepository.existsByOfferIdAndUserId(1L, 2L)).thenReturn(false);
        when(couponRepository.saveAndFlush(any(TouristCoupon.class)))
                .thenThrow(constraintViolation("uq_tourist_coupon_offer_user"));

        assertThatThrownBy(() -> offerService.issue(2L, 1L))
                .isInstanceOfSatisfying(OfferException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OfferErrorCode.COUPON_ALREADY_ISSUED));
    }

    @Test
    void unrelatedConstraintViolationIsNotMappedToDuplicateCouponError() {
        TouristOffer offer = publishedOffer(2);
        DataIntegrityViolationException violation = constraintViolation("fk_tourist_coupon_offer");
        when(offerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(couponRepository.existsByOfferIdAndUserId(1L, 2L)).thenReturn(false);
        when(couponRepository.saveAndFlush(any(TouristCoupon.class))).thenThrow(violation);

        assertThatThrownBy(() -> offerService.issue(2L, 1L)).isSameAs(violation);
    }

    @Test
    void soldOutOfferIsRejected() {
        TouristOffer offer = publishedOffer(1);
        offer.issueCoupon(NOW.minusMinutes(1));
        when(offerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(couponRepository.existsByOfferIdAndUserId(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> offerService.issue(2L, 1L))
                .isInstanceOfSatisfying(OfferException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OfferErrorCode.OFFER_SOLD_OUT));
    }

    @Test
    void unavailableOfferIsRejected() {
        TouristOffer draft = draftOffer(2);
        when(offerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(draft));
        when(couponRepository.existsByOfferIdAndUserId(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> offerService.issue(2L, 1L))
                .isInstanceOfSatisfying(OfferException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OfferErrorCode.OFFER_NOT_AVAILABLE));
    }

    @Test
    void offerWhoseMerchantLostEligibilityIsRejected() {
        TouristOffer offer = publishedOffer(2);
        when(offerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(merchantAccessPolicy.isActiveOwnerOfPlace(10L, 100L, NOW)).thenReturn(false);

        assertThatThrownBy(() -> offerService.issue(2L, 1L))
                .isInstanceOfSatisfying(OfferException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OfferErrorCode.OFFER_NOT_AVAILABLE));

        verify(couponRepository, never()).existsByOfferIdAndUserId(1L, 2L);
    }

    private TouristOffer publishedOffer(int quantity) {
        TouristOffer offer = draftOffer(quantity);
        offer.publish(NOW.minusMinutes(1));
        return offer;
    }

    private TouristOffer draftOffer(int quantity) {
        return TouristOffer.draft(
                10L,
                100L,
                "관광객 Offer",
                "설명",
                "혜택",
                NOW.minusHours(1),
                NOW.plusDays(10),
                quantity,
                7,
                NOW.minusDays(1)
        );
    }

    private DataIntegrityViolationException constraintViolation(String constraintName) {
        return new DataIntegrityViolationException(
                "coupon insert failed",
                new ConstraintViolationException(
                        "constraint violation",
                        new SQLException("duplicate key"),
                        "insert into tourist_coupon",
                        constraintName
                )
        );
    }
}
