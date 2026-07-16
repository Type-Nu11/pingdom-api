package com.typenull.pingdom.offer.application;

import com.typenull.pingdom.offer.api.dto.CouponRedeemRequest;
import com.typenull.pingdom.offer.api.dto.CouponResponse;
import com.typenull.pingdom.offer.api.dto.OfferCreateRequest;
import com.typenull.pingdom.offer.api.dto.OfferPageResponse;
import com.typenull.pingdom.offer.api.dto.OfferResponse;
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

@Service
@RequiredArgsConstructor
public class MerchantOfferService {

    private final TouristOfferRepository offerRepository;
    private final TouristCouponRepository couponRepository;
    private final MerchantOfferAccessPolicy accessPolicy;
    private final Clock clock;

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
                    now
            );
            return OfferResponse.from(offerRepository.save(offer));
        } catch (IllegalArgumentException exception) {
            throw new OfferException(OfferErrorCode.INVALID_OFFER_INPUT);
        }
    }

    @Transactional(readOnly = true)
    public OfferPageResponse list(Long merchantOwnerUserId, int page, int limit) {
        Page<TouristOffer> result = offerRepository.findAllByMerchantOwnerUserId(
                merchantOwnerUserId,
                pageRequest(page, limit, "createdAt")
        );
        return offerPage(result);
    }

    @Transactional(readOnly = true)
    public OfferResponse get(Long merchantOwnerUserId, Long offerId) {
        return OfferResponse.from(offerRepository.findByIdAndMerchantOwnerUserId(offerId, merchantOwnerUserId)
                .orElseThrow(() -> new OfferException(OfferErrorCode.OFFER_NOT_FOUND)));
    }

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
