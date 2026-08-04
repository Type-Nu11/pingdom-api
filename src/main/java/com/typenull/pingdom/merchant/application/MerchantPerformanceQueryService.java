package com.typenull.pingdom.merchant.application;

import com.typenull.pingdom.availability.application.AvailabilityAccessPolicy;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.merchant.api.dto.MerchantPerformanceResponse;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.reservation.domain.ReservationStatus;
import com.typenull.pingdom.reservation.infrastructure.ReservationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantPerformanceQueryService {
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;
    private final PlaceRecommendationExposureRepository exposureRepository;
    private final PlaceRecommendationClickRepository clickRepository;
    private final MapBookmarkRepository bookmarkRepository;
    private final ReservationRepository reservationRepository;
    private final AvailabilityAccessPolicy availabilityAccessPolicy;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MerchantPerformanceResponse get(Long ownerId) {
        availabilityAccessPolicy.requireActiveMerchantOwner(ownerId, LocalDateTime.now(clock));

        Collection<Long> placeIds = ownerPlaceRepository.findAllByMerchantOwnerUserIdOrderByPlaceIdAsc(ownerId)
                .stream()
                .map(place -> place.getPlaceId())
                .toList();
        long exposureCount = sumExposureCounts(placeIds);
        long clickCount = sumClickCounts(placeIds);
        long bookmarkCount = sumBookmarkCounts(placeIds);
        long reservationCount = reservationRepository.countOwnedByMerchantOwnerUserId(ownerId);
        long confirmedReservationCount = reservationRepository.countOwnedByMerchantOwnerUserIdAndStatus(
                ownerId, ReservationStatus.CONFIRMED);

        return new MerchantPerformanceResponse(
                placeIds.size(),
                exposureCount,
                clickCount,
                bookmarkCount,
                reservationCount,
                confirmedReservationCount,
                percentage(clickCount, exposureCount),
                percentage(confirmedReservationCount, clickCount)
        );
    }

    private long sumExposureCounts(Collection<Long> placeIds) {
        if (placeIds.isEmpty()) return 0;
        return exposureRepository.countExposuresByPlaceIds(placeIds).stream()
                .mapToLong(PlaceRecommendationExposureRepository.PlaceExposureCountProjection::getExposureCount)
                .sum();
    }

    private long sumClickCounts(Collection<Long> placeIds) {
        if (placeIds.isEmpty()) return 0;
        return clickRepository.countClicksByPlaceIds(placeIds).stream()
                .mapToLong(PlaceRecommendationClickRepository.PlaceClickCountProjection::getClickCount)
                .sum();
    }

    private long sumBookmarkCounts(Collection<Long> placeIds) {
        if (placeIds.isEmpty()) return 0;
        return bookmarkRepository.findBookmarkCountsByPlaceIds(placeIds).stream()
                .mapToLong(MapBookmarkRepository.PlaceBookmarkCountProjection::getBookmarkCount)
                .sum();
    }

    private double percentage(long numerator, long denominator) {
        if (denominator == 0) return 0.0;
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
