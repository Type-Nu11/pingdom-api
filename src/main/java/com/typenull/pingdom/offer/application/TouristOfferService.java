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
