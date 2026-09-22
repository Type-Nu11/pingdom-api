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
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
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
    @Mock private MapPlaceRepository mapPlaceRepository;
    @Mock private TouristEligibilityPolicy eligibilityPolicy;
    @Mock private MerchantOfferAccessPolicy merchantAccessPolicy;
    @Mock private PlaceConversionEventService conversionEventService;
    @Mock private Clock clock;

    @InjectMocks private TouristOfferService offerService;

    /**
     * 쿠폰 발급 기준 시각과 Offer 점주의 현재 활성 소유 상태를 공통 입력으로 설정한다.
     */
    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-07-16T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(merchantAccessPolicy.isActiveOwnerOfPlace(10L, 100L, NOW)).thenReturn(true);
    }

    /**
     * 여행 일정 자격 정책을 호출하고 발급한 쿠폰의 Offer ID·ISSUED 상태와 발급 수 1을 확인한다.
     */
    @Test
    void issuesEligibleTouristCoupon() {
        TouristOffer offer = publishedOffer(2);
        when(offerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(couponRepository.existsByOfferIdAndUserId(1L, 2L)).thenReturn(false);
        stubMissingPlace();
        when(couponRepository.saveAndFlush(any(TouristCoupon.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = offerService.issue(2L, 1L);

        verify(eligibilityPolicy).requireEligible(2L, NOW, CouponEligibilityPolicy.ACTIVE_TRAVEL_SCHEDULE);
        assertThat(response.offerId()).isEqualTo(1L);
        assertThat(response.status().name()).isEqualTo("ISSUED");
        assertThat(offer.getIssuedQuantity()).isEqualTo(1);
    }

    /**
     * PUBLIC Offer 발급에서 공개 자격 정책을 사용하고 쿠폰 만료를 Offer 종료 시각으로 설정하는지 검증한다.
     */
    @Test
    void issuesWithPublicEligibility() {
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
        stubMissingPlace();
        when(couponRepository.saveAndFlush(any(TouristCoupon.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = offerService.issue(2L, 1L);

        verify(eligibilityPolicy).requireEligible(2L, NOW, CouponEligibilityPolicy.PUBLIC);
        assertThat(response.expiresAt()).isEqualTo(offer.getEndsAt());
    }

    /**
     * 이미 쿠폰이 있으면 COUPON_ALREADY_ISSUED이며 발급 수 0과 저장 미호출을 유지하는지 검증한다.
     */
    @Test
    void rejectsDuplicateBeforeIssuance() {
        TouristOffer offer = publishedOffer(2);
        when(offerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(couponRepository.existsByOfferIdAndUserId(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> offerService.issue(2L, 1L))
                .isInstanceOfSatisfying(OfferException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OfferErrorCode.COUPON_ALREADY_ISSUED));

        assertThat(offer.getIssuedQuantity()).isZero();
        verify(couponRepository, never()).saveAndFlush(any());
    }

    /**
     * 사전 조회 뒤 발급 저장에서 Offer·사용자 고유 제약 위반이 발생해도 COUPON_ALREADY_ISSUED로 변환하는지 검증한다.
     */
    @Test
    void mapsDuplicateCouponConstraint() {
        TouristOffer offer = publishedOffer(2);
        when(offerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(couponRepository.existsByOfferIdAndUserId(1L, 2L)).thenReturn(false);
        stubMissingPlace();
        when(couponRepository.saveAndFlush(any(TouristCoupon.class)))
                .thenThrow(constraintViolation("uq_tourist_coupon_offer_user"));

        assertThatThrownBy(() -> offerService.issue(2L, 1L))
                .isInstanceOfSatisfying(OfferException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OfferErrorCode.COUPON_ALREADY_ISSUED));
    }

    /**
     * 쿠폰 외래 키 위반은 중복 발급으로 오인하지 않고 원래 무결성 예외 객체를 전파하는지 검증한다.
     */
    @Test
    void preservesUnrelatedCouponConstraint() {
        TouristOffer offer = publishedOffer(2);
        DataIntegrityViolationException violation = constraintViolation("fk_tourist_coupon_offer");
        when(offerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(couponRepository.existsByOfferIdAndUserId(1L, 2L)).thenReturn(false);
        stubMissingPlace();
        when(couponRepository.saveAndFlush(any(TouristCoupon.class))).thenThrow(violation);

        assertThatThrownBy(() -> offerService.issue(2L, 1L)).isSameAs(violation);
    }

    /**
     * 한정 수량이 이미 소진된 Offer에 새 발급을 요청하면 OFFER_SOLD_OUT인지 검증한다.
     */
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

    /**
     * 게시하지 않은 초안 Offer에 발급을 요청하면 OFFER_NOT_AVAILABLE인지 검증한다.
     */
    @Test
    void unavailableOfferIsRejected() {
        TouristOffer draft = draftOffer(2);
        when(offerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(draft));
        when(couponRepository.existsByOfferIdAndUserId(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> offerService.issue(2L, 1L))
                .isInstanceOfSatisfying(OfferException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OfferErrorCode.OFFER_NOT_AVAILABLE));
    }

    /**
     * 점주가 현재 활성 소유자가 아니면 OFFER_NOT_AVAILABLE로 거절하고 기존 쿠폰 조회에도 도달하지 않는지 검증한다.
     */
    @Test
    void rejectsIneligibleOfferMerchant() {
        TouristOffer offer = publishedOffer(2);
        when(offerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(merchantAccessPolicy.isActiveOwnerOfPlace(10L, 100L, NOW)).thenReturn(false);

        assertThatThrownBy(() -> offerService.issue(2L, 1L))
                .isInstanceOfSatisfying(OfferException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OfferErrorCode.OFFER_NOT_AVAILABLE));

        verify(couponRepository, never()).existsByOfferIdAndUserId(1L, 2L);
    }

    /**
     * 주어진 수량의 초안을 현재보다 1분 전에 게시하여 유효한 발급 대상 Offer를 제공한다.
     */
    private TouristOffer publishedOffer(int quantity) {
        TouristOffer offer = draftOffer(quantity);
        offer.publish(NOW.minusMinutes(1));
        return offer;
    }

    /**
     * 장소 상세 조회가 없도록 설정해 장소 부가 정보 없이 진행되는 쿠폰 발급 경로를 재현한다.
     */
    private void stubMissingPlace() {
        when(mapPlaceRepository.findById(100L)).thenReturn(Optional.empty());
    }

    /**
     * 점주 10·장소 100의 발급 기간 내 초안을 지정 수량과 쿠폰 유효기간 7일로 생성한다.
     */
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

    /**
     * 지정한 제약 이름을 담은 Hibernate 원인을 Spring 무결성 예외로 감싸 중복 판별 경로를 재현한다.
     */
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
