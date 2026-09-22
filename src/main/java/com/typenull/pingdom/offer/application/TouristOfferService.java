package com.typenull.pingdom.offer.application;

import com.typenull.pingdom.offer.api.dto.CouponPageResponse;
import com.typenull.pingdom.offer.api.dto.CouponResponse;
import com.typenull.pingdom.offer.api.dto.OfferPageResponse;
import com.typenull.pingdom.offer.api.dto.OfferResponse;
import com.typenull.pingdom.offer.domain.OfferStatus;
import com.typenull.pingdom.offer.domain.CouponStatus;
import com.typenull.pingdom.offer.domain.TouristCoupon;
import com.typenull.pingdom.offer.domain.TouristOffer;
import com.typenull.pingdom.offer.domain.exception.OfferErrorCode;
import com.typenull.pingdom.offer.domain.exception.OfferException;
import com.typenull.pingdom.offer.infrastructure.TouristCouponRepository;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import com.typenull.pingdom.place.application.service.conversion.PlaceConversionEventService;
import com.typenull.pingdom.place.domain.conversion.PlaceConversionEventType;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import jakarta.persistence.criteria.Predicate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관광객에게 현재 발급 가능한 혜택을 조회하고, 혜택 재고와 사용자별 쿠폰 발급을 함께 처리합니다.
 * 발급 시 혜택 행을 잠그고 사용자 자격·현재 점주 자격을 다시 확인하며, 쿠폰에는 발급 당시 표시 정보를 보관합니다.
 */
@Service
@RequiredArgsConstructor
public class TouristOfferService {

    private final TouristOfferRepository offerRepository;
    private final TouristCouponRepository couponRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final TouristEligibilityPolicy eligibilityPolicy;
    private final MerchantOfferAccessPolicy merchantAccessPolicy;
    private final PlaceConversionEventService conversionEventService;
    private final Clock clock;

    /**
     * 공개 기간·재고·현재 점주 자격과 소유 관계를 만족하는 혜택을 선택 장소 조건으로 조회합니다.
     * 페이지는 최소 1, 크기는 1~100으로 보정하고 종료 시각·ID 오름차순의 발급 가능 목록을 반환합니다.
     */
    @Transactional(readOnly = true)
    public OfferPageResponse list(Long placeId, int page, int limit) {
        Page<TouristOffer> result = offerRepository.findAvailable(
                OfferStatus.PUBLISHED,
                LocalDateTime.now(clock),
                placeId,
                pageRequest(page, limit, "endsAt")
        );
        return new OfferPageResponse(
                result.getContent().stream().map(OfferResponse::from).toList(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public OfferResponse get(Long offerId) {
        return OfferResponse.from(offerRepository.findAvailableById(
                        offerId,
                        OfferStatus.PUBLISHED,
                        LocalDateTime.now(clock)
                )
                .orElseThrow(() -> new OfferException(OfferErrorCode.OFFER_NOT_FOUND)));
    }

    /**
     * 혜택 행 잠금 안에서 재고를 차감하고 쿠폰을 저장한 뒤 전환 이벤트를 발행합니다.
     * 동일 혜택·사용자 조합은 사전 조회와 DB 유일 제약으로 거절하며, 다른 무결성 오류는 그대로 전파합니다.
     */
    @Transactional
    public CouponResponse issue(Long userId, Long offerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        TouristOffer offer = offerRepository.findByIdForUpdate(offerId)
                .orElseThrow(() -> new OfferException(OfferErrorCode.OFFER_NOT_FOUND));
        eligibilityPolicy.requireEligible(userId, now, offer.getEligibilityPolicy());
        if (!merchantAccessPolicy.isActiveOwnerOfPlace(
                offer.getMerchantOwnerUserId(),
                offer.getPlaceId(),
                now
        )) {
            throw new OfferException(OfferErrorCode.OFFER_NOT_AVAILABLE);
        }
        if (couponRepository.existsByOfferIdAndUserId(offerId, userId)) {
            throw new OfferException(OfferErrorCode.COUPON_ALREADY_ISSUED);
        }

        LocalDateTime expiresAt;
        try {
            expiresAt = offer.issueCoupon(now);
        } catch (IllegalArgumentException exception) {
            throw new OfferException(OfferErrorCode.OFFER_SOLD_OUT);
        } catch (IllegalStateException exception) {
            throw new OfferException(OfferErrorCode.OFFER_NOT_AVAILABLE);
        }

        TouristCoupon coupon = TouristCoupon.issue(
                offerId,
                offer.getTitle(),
                offer.getBenefitDescription(),
                offer.getPlaceId(),
                mapPlaceRepository.findById(offer.getPlaceId()).map(place -> place.getName()).orElse(null),
                userId,
                UUID.randomUUID().toString(),
                now,
                expiresAt
        );
        try {
            TouristCoupon saved = couponRepository.saveAndFlush(coupon);
            conversionEventService.publish(
                    userId,
                    offer.getPlaceId(),
                    PlaceConversionEventType.BENEFIT,
                    saved.getId(),
                    now
            );
            return CouponResponse.from(saved, now);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_tourist_coupon_offer_user")) {
                throw new OfferException(OfferErrorCode.COUPON_ALREADY_ISSUED);
            }
            throw exception;
        }
    }

    /**
     * 본인 쿠폰을 발급 기간의 양 끝을 포함해 조회하고 현재 시각 기준 사용·만료 상태로 필터링해 반환합니다.
     * 역전된 기간은 거절하며 만료 상태는 DB를 갱신하지 않고 계산하고, 발급 시각·ID 내림차순으로 페이지를 구성합니다.
     */
    @Transactional(readOnly = true)
    public CouponPageResponse listCoupons(
            Long userId,
            CouponStatus status,
            LocalDateTime issuedFrom,
            LocalDateTime issuedTo,
            int page,
            int limit
    ) {
        if (issuedFrom != null && issuedTo != null && issuedFrom.isAfter(issuedTo)) {
            throw new OfferException(OfferErrorCode.COUPON_LIST_FILTER_INVALID);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Page<TouristCoupon> result = couponRepository.findAll(
                couponListSpecification(userId, status, issuedFrom, issuedTo, now),
                pageRequest(page, limit, "issuedAt", Sort.Direction.DESC)
        );
        return new CouponPageResponse(
                result.getContent().stream().map(coupon -> CouponResponse.from(coupon, now)).toList(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public CouponResponse getCoupon(Long userId, Long couponId) {
        LocalDateTime now = LocalDateTime.now(clock);
        TouristCoupon coupon = couponRepository.findByIdAndUserId(couponId, userId)
                .orElseThrow(() -> new OfferException(OfferErrorCode.COUPON_NOT_FOUND));
        return CouponResponse.from(coupon, now);
    }

    private PageRequest pageRequest(int page, int limit, String sortProperty) {
        return pageRequest(page, limit, sortProperty, Sort.Direction.ASC);
    }

    /**
     * 발급 시각 범위는 양 끝을 포함합니다. 만료는 저장 상태를 바꾸지 않고 조회 시각으로 계산하므로,
     * ISSUED와 EXPIRED를 나눌 때 expiresAt과 now의 동일 시각은 EXPIRED에 포함합니다.
     */
    private Specification<TouristCoupon> couponListSpecification(
            Long userId,
            CouponStatus status,
            LocalDateTime issuedFrom,
            LocalDateTime issuedTo,
            LocalDateTime now
    ) {
        return (root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(criteriaBuilder.equal(root.get("userId"), userId));
            if (issuedFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("issuedAt"), issuedFrom));
            }
            if (issuedTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("issuedAt"), issuedTo));
            }
            if (status == CouponStatus.ISSUED) {
                predicates.add(criteriaBuilder.equal(root.get("status"), CouponStatus.ISSUED));
                predicates.add(criteriaBuilder.greaterThan(root.get("expiresAt"), now));
            } else if (status == CouponStatus.REDEEMED) {
                predicates.add(criteriaBuilder.equal(root.get("status"), CouponStatus.REDEEMED));
            } else if (status == CouponStatus.EXPIRED) {
                predicates.add(criteriaBuilder.equal(root.get("status"), CouponStatus.ISSUED));
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("expiresAt"), now));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private PageRequest pageRequest(int page, int limit, String sortProperty, Sort.Direction direction) {
        return PageRequest.of(
                Math.max(page - 1, 0),
                Math.min(Math.max(limit, 1), 100),
                Sort.by(
                        new Sort.Order(direction, sortProperty),
                        new Sort.Order(direction, "id")
                )
        );
    }

    private boolean hasConstraint(Throwable throwable, String constraintName) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
