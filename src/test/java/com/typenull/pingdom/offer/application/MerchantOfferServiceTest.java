package com.typenull.pingdom.offer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.typenull.pingdom.offer.api.dto.CouponRedeemRequest;
import com.typenull.pingdom.offer.api.dto.OfferCreateRequest;
import com.typenull.pingdom.offer.api.dto.OfferResponse;
import com.typenull.pingdom.offer.domain.CouponEligibilityPolicy;
import com.typenull.pingdom.offer.domain.CouponExpiryPolicy;
import com.typenull.pingdom.offer.domain.CouponInventoryPolicy;
import com.typenull.pingdom.offer.domain.CouponStatus;
import com.typenull.pingdom.offer.domain.OfferStatus;
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
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class MerchantOfferServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 16, 12, 0);
    private static final String CODE = "3fa85f64-5717-4562-b3fc-2c963f66afa6";

    @Mock private TouristOfferRepository offerRepository;
    @Mock private TouristCouponRepository couponRepository;
    @Mock private MerchantOfferAccessPolicy accessPolicy;
    @Mock private Clock clock;

    @InjectMocks private MerchantOfferService offerService;

    /**
     * Offer와 쿠폰의 유효 기간 판단 시각을 UTC로 고정하고 시각을 쓰지 않는 테스트의 공통 stubbing을 허용한다.
     */
    @BeforeEach
    void setUpClock() {
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-07-16T12:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    /**
     * 대문자 쿠폰 코드를 정규화해 조회하고 장소 소유권 확인 후 REDEEMED와 사용 점주 ID를 기록하는지 검증한다.
     * 이 테스트에서는 두 번째 사용 요청을 실행하지 않는다.
     */
    @Test
    void redeemsOwnedCoupon() {
        TouristCoupon coupon = TouristCoupon.issue(1L, 2L, CODE, NOW.minusDays(1), NOW.plusDays(1));
        TouristOffer offer = offer();
        when(couponRepository.findByCodeForUpdate(CODE)).thenReturn(Optional.of(coupon));
        when(offerRepository.findByIdAndMerchantOwnerUserId(1L, 10L)).thenReturn(Optional.of(offer));

        var response = offerService.redeem(10L, new CouponRedeemRequest(CODE.toUpperCase()));

        verify(accessPolicy).requireOwnedPlace(10L, 100L, NOW);
        assertThat(response.status()).isEqualTo(CouponStatus.REDEEMED);
        assertThat(coupon.getRedeemedBy()).isEqualTo(10L);
    }

    /**
     * 소유자 조건의 Offer 조회가 비어 있으면 COUPON_NOT_FOUND이며 쿠폰이 ISSUED 상태로 유지되는지 검증한다.
     */
    @Test
    void rejectsUnownedCouponRedemption() {
        TouristCoupon coupon = TouristCoupon.issue(1L, 2L, CODE, NOW.minusDays(1), NOW.plusDays(1));
        when(couponRepository.findByCodeForUpdate(CODE)).thenReturn(Optional.of(coupon));
        when(offerRepository.findByIdAndMerchantOwnerUserId(1L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> offerService.redeem(10L, new CouponRedeemRequest(CODE)))
                .isInstanceOfSatisfying(OfferException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OfferErrorCode.COUPON_NOT_FOUND));

        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ISSUED);
    }

    /**
     * 만료된 쿠폰 사용 요청을 COUPON_NOT_REDEEMABLE 도메인 오류로 변환하는지 검증한다.
     */
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

    /**
     * 종료 시각이 지난 Offer 생성 요청이 INVALID_OFFER_PERIOD로 거절되는지 검증한다.
     */
    @Test
    void rejectsEndedOfferCreation() {
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

    /**
     * 공개·무제한·Offer 종료 만료 정책을 명시한 생성이 각 정책과 null 총수량을 저장하고 null 잔여 수량을 응답하는지 검증한다.
     */
    @Test
    void createsUnlimitedPublicOffer() {
        OfferCreateRequest request = new OfferCreateRequest(
                100L,
                "상시 웰컴 혜택",
                "누구나 발급할 수 있는 혜택",
                "음료 1잔 무료",
                NOW.minusHours(1),
                NOW.plusDays(7),
                null,
                3,
                CouponEligibilityPolicy.PUBLIC,
                CouponInventoryPolicy.UNLIMITED,
                CouponExpiryPolicy.OFFER_END
        );
        when(offerRepository.save(org.mockito.ArgumentMatchers.any(TouristOffer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OfferResponse response = offerService.create(10L, request);

        ArgumentCaptor<TouristOffer> captor = ArgumentCaptor.forClass(TouristOffer.class);
        verify(offerRepository).save(captor.capture());
        TouristOffer saved = captor.getValue();
        assertThat(saved.getTotalQuantity()).isNull();
        assertThat(saved.getEligibilityPolicy()).isEqualTo(CouponEligibilityPolicy.PUBLIC);
        assertThat(saved.getInventoryPolicy()).isEqualTo(CouponInventoryPolicy.UNLIMITED);
        assertThat(saved.getExpiryPolicy()).isEqualTo(CouponExpiryPolicy.OFFER_END);
        assertThat(response.remainingQuantity()).isNull();
    }

    /**
     * 소유자·장소·게시 상태 필터와 0번 페이지·20건·생성시각/ID 내림차순을 저장소에 전달하고 결과를 매핑하는지 검증한다.
     */
    @Test
    void forwardsOfferFiltersAndPagination() {
        TouristOffer offer = offer();
        when(offerRepository.findAllByMerchantOwnerUserIdWithFilters(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq(OfferStatus.PUBLISHED),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(offer)));

        var response = offerService.list(10L, 1, 20, 100L, OfferStatus.PUBLISHED);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(offerRepository).findAllByMerchantOwnerUserIdWithFilters(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq(OfferStatus.PUBLISHED),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageableCaptor.getValue().getSort().toList())
                .extracting(order -> order.getProperty() + ":" + order.getDirection())
                .containsExactly("createdAt:DESC", "id:DESC");
        assertThat(response.totalElements()).isOne();
        assertThat(response.offers()).extracting(OfferResponse::placeId).containsExactly(100L);
    }

    /**
     * 점주 10·장소 100의 유효 기간 내 초안 Offer를 만들어 소유권과 쿠폰 처리의 입력으로 제공한다.
     */
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
