package com.typenull.pingdom.offer.application;

import com.typenull.pingdom.offer.api.dto.CouponRedeemRequest;
import com.typenull.pingdom.offer.api.dto.CouponResponse;
import com.typenull.pingdom.offer.api.dto.OfferCreateRequest;
import com.typenull.pingdom.offer.api.dto.OfferPageResponse;
import com.typenull.pingdom.offer.api.dto.OfferResponse;
import com.typenull.pingdom.offer.domain.CouponEligibilityPolicy;
import com.typenull.pingdom.offer.domain.CouponExpiryPolicy;
import com.typenull.pingdom.offer.domain.CouponInventoryPolicy;
import com.typenull.pingdom.offer.domain.OfferStatus;
import com.typenull.pingdom.offer.domain.TouristCoupon;
import com.typenull.pingdom.offer.domain.TouristOffer;
import com.typenull.pingdom.offer.domain.exception.OfferErrorCode;
import com.typenull.pingdom.offer.domain.exception.OfferException;
import com.typenull.pingdom.offer.infrastructure.TouristCouponRepository;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 점주의 혜택 작성·공개·종료와 쿠폰 사용을 처리합니다.
 * 변경 요청마다 현재 장소 소유권과 점주 자격을 검증하며, 혜택 또는 쿠폰 행 잠금으로 상태 전이를 직렬화합니다.
 */
@Service
@RequiredArgsConstructor
public class MerchantOfferService {

    private final TouristOfferRepository offerRepository;
    private final TouristCouponRepository couponRepository;
    private final MerchantOfferAccessPolicy accessPolicy;
    private final Clock clock;

    /**
     * 현재 점주의 장소 소유권과 유효한 기간을 확인해 혜택 초안을 저장하고 응답을 반환합니다.
     * 정책 생략 시 활성 여행 일정·제한 재고·혜택 종료일로 상한을 둔 발급일 기준 만료 정책을 적용하며 잘못된 입력은 거절합니다.
     */
    @Transactional
    public OfferResponse create(Long merchantOwnerUserId, OfferCreateRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        accessPolicy.requireOwnedPlace(merchantOwnerUserId, request.placeId(), now);
        if (!request.endsAt().isAfter(request.startsAt()) || !request.endsAt().isAfter(now)) {
            throw new OfferException(OfferErrorCode.INVALID_OFFER_PERIOD);
        }
        try {
            TouristOffer offer = TouristOffer.draft(
                    merchantOwnerUserId,
                    request.placeId(),
                    request.title(),
                    request.description(),
                    request.benefitDescription(),
                    request.startsAt(),
                    request.endsAt(),
                    request.totalQuantity(),
                    request.couponValidityDays(),
                    request.eligibilityPolicy() == null
                            ? CouponEligibilityPolicy.ACTIVE_TRAVEL_SCHEDULE
                            : request.eligibilityPolicy(),
                    request.inventoryPolicy() == null
                            ? CouponInventoryPolicy.LIMITED
                            : request.inventoryPolicy(),
                    request.expiryPolicy() == null
                            ? CouponExpiryPolicy.ISSUE_PLUS_DAYS_CAPPED_BY_OFFER_END
                            : request.expiryPolicy(),
                    now
            );
            return OfferResponse.from(offerRepository.save(offer));
        } catch (IllegalArgumentException exception) {
            throw new OfferException(OfferErrorCode.INVALID_OFFER_INPUT);
        }
    }

    /**
     * 점주가 현재 소유한 장소의 혜택을 선택 장소·상태 조건으로 조회해 생성 시각·ID 내림차순 페이지로 반환합니다.
     * 페이지는 최소 1, 크기는 1~100으로 보정합니다.
     */
    @Transactional(readOnly = true)
    public OfferPageResponse list(Long merchantOwnerUserId, int page, int limit, Long placeId, OfferStatus status) {
        Page<TouristOffer> result = offerRepository.findAllByMerchantOwnerUserIdWithFilters(
                merchantOwnerUserId,
                placeId,
                status,
                pageRequest(page, limit, "createdAt")
        );
        return offerPage(result);
    }

    @Transactional(readOnly = true)
    public OfferResponse get(Long merchantOwnerUserId, Long offerId) {
        return OfferResponse.from(offerRepository.findByIdAndMerchantOwnerUserId(offerId, merchantOwnerUserId)
                .orElseThrow(() -> new OfferException(OfferErrorCode.OFFER_NOT_FOUND)));
    }

    /**
     * 본인 혜택을 잠그고 현재 장소 소유 자격을 재확인해 종료 전 초안을 공개 상태로 전환하고 반환합니다.
     * 없는 혜택·소유권 오류와 공개 불가 상태는 거절합니다.
     */
    @Transactional
    public OfferResponse publish(Long merchantOwnerUserId, Long offerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        TouristOffer offer = findOwnedForUpdate(merchantOwnerUserId, offerId);
        accessPolicy.requireOwnedPlace(merchantOwnerUserId, offer.getPlaceId(), now);
        try {
            offer.publish(now);
        } catch (IllegalStateException exception) {
            throw new OfferException(OfferErrorCode.INVALID_OFFER_STATE);
        }
        return OfferResponse.from(offer);
    }

    /**
     * 본인 혜택을 잠그고 현재 장소 소유 자격을 재확인한 뒤 공개 혜택을 종료해 반환합니다.
     * 이미 종료됐으면 그대로 반환하고 그 밖의 비공개 상태는 상태 오류로 거절합니다.
     */
    @Transactional
    public OfferResponse close(Long merchantOwnerUserId, Long offerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        TouristOffer offer = findOwnedForUpdate(merchantOwnerUserId, offerId);
        accessPolicy.requireOwnedPlace(merchantOwnerUserId, offer.getPlaceId(), now);
        try {
            offer.close(now);
        } catch (IllegalStateException exception) {
            throw new OfferException(OfferErrorCode.INVALID_OFFER_STATE);
        }
        return OfferResponse.from(offer);
    }

    /**
     * 입력 코드를 정규화한 뒤 쿠폰을 잠그고 해당 혜택의 소유 점주인지 검증합니다.
     * 만료 또는 이미 사용한 쿠폰은 충돌로 거절하며, 혜택의 현재 공개 여부는 쿠폰 사용 조건에 포함하지 않습니다.
     */
    @Transactional
    public CouponResponse redeem(Long merchantOwnerUserId, CouponRedeemRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        TouristCoupon coupon = couponRepository.findByCodeForUpdate(request.code().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new OfferException(OfferErrorCode.COUPON_NOT_FOUND));
        TouristOffer offer = offerRepository.findByIdAndMerchantOwnerUserId(coupon.getOfferId(), merchantOwnerUserId)
                .orElseThrow(() -> new OfferException(OfferErrorCode.COUPON_NOT_FOUND));
        accessPolicy.requireOwnedPlace(merchantOwnerUserId, offer.getPlaceId(), now);
        try {
            coupon.redeem(merchantOwnerUserId, now);
        } catch (IllegalStateException exception) {
            throw new OfferException(OfferErrorCode.COUPON_NOT_REDEEMABLE);
        }
        return CouponResponse.from(coupon, now);
    }

    private TouristOffer findOwnedForUpdate(Long merchantOwnerUserId, Long offerId) {
        return offerRepository.findOwnedByIdForUpdate(offerId, merchantOwnerUserId)
                .orElseThrow(() -> new OfferException(OfferErrorCode.OFFER_NOT_FOUND));
    }

    private PageRequest pageRequest(int page, int limit, String sortProperty) {
        return PageRequest.of(
                Math.max(page - 1, 0),
                Math.min(Math.max(limit, 1), 100),
                Sort.by(Sort.Order.desc(sortProperty), Sort.Order.desc("id"))
        );
    }

    private OfferPageResponse offerPage(Page<TouristOffer> result) {
        return new OfferPageResponse(
                result.getContent().stream().map(OfferResponse::from).toList(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }
}
