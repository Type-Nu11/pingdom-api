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

/**
 * 현재 소유 장소의 노출·클릭·북마크와 점주 예약 건수를 집계.
 * 기간 제한 없이 집계하며 클릭률과 확정 예약 전환율은 백분율로 소수 둘째 자리 반올림. 분모가 0이면 0을 반환.
 */
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

    /**
     * 활성 점주 자격을 확인해 현재 소유 장소의 노출·클릭·북마크와 관리 예약·확정 예약 건수를 집계.
     * 기간 제한 없이 클릭률과 클릭 대비 확정 예약률을 백분율로 반환하며 분모가 0이면 비율도 0.
     */
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
