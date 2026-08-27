package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.api.dto.trend.PlaceTrendPeriod;
import com.typenull.pingdom.place.api.dto.trend.PlaceTrendResponse;
import com.typenull.pingdom.place.domain.place.core.MapBookmarkTrendTracking;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkTrendTrackingRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceTrendQueryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceTrendQueryService {

    private static final int MAX_LIMIT = 50;

    private final PlaceTrendQueryRepository placeTrendQueryRepository;
    private final MapBookmarkTrendTrackingRepository trackingRepository;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PlaceTrendResponse find(PlaceTrendPeriod period, int page, int limit, long userId) {
        PlaceTrendPeriod safePeriod = period == null ? PlaceTrendPeriod.WEEK : period;
        int safePage = Math.max(page, 1);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        LocalDateTime generatedAt = LocalDateTime.now(clock);
        MapBookmarkTrendTracking tracking = trackingRepository.findById(Boolean.TRUE)
                .orElseThrow(() -> new IllegalStateException("bookmark trend tracking is missing"));
        LocalDateTime requestedStart = switch (safePeriod) {
            case WEEK -> generatedAt.minus(7, ChronoUnit.DAYS);
        };
        LocalDateTime periodStart = requestedStart.isAfter(tracking.getStartedAt())
                ? requestedStart
                : tracking.getStartedAt();
        long totalElements = placeTrendQueryRepository.countTrends(periodStart, generatedAt);
        List<PlaceTrendQueryRepository.PlaceTrendProjection> trends = placeTrendQueryRepository.findTrends(
                periodStart,
                generatedAt,
                userId,
                PageRequest.of(safePage - 1, safeLimit)
        );
        int totalPages = Math.max(1, (int) Math.ceil((double) totalElements / safeLimit));
        List<PlaceTrendResponse.Item> places = java.util.stream.IntStream.range(0, trends.size())
                .mapToObj(index -> toItem(trends.get(index), ((safePage - 1) * safeLimit) + index + 1))
                .toList();
        return new PlaceTrendResponse(
                "NATIONAL",
                safePeriod,
                periodStart,
                generatedAt,
                generatedAt,
                places,
                safePage,
                safeLimit,
                totalElements,
                totalPages,
                safePage < totalPages
        );
    }

    private PlaceTrendResponse.Item toItem(PlaceTrendQueryRepository.PlaceTrendProjection projection, int rank) {
        return new PlaceTrendResponse.Item(
                rank,
                projection.getPlaceId(),
                projection.getPlaceName(),
                projection.getCategory(),
                projection.getImageUrl(),
                projection.getAddress(),
                projection.getBookmarkAdds(),
                projection.getBookmarkRemoves(),
                projection.getNetBookmarkGrowth(),
                projection.getBookmarkCount(),
                projection.getBookmarked()
        );
    }
}
