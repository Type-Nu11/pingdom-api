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

/**
 * 북마크 추적 시작 이후의 주간 증감 추세를 읽습니다.
 * 반복 읽기 트랜잭션에서 같은 생성 시각·기간으로 개수와 목록을 조회하고 빈 결과의 totalPages도 1로 표시합니다.
 */
@Service
@RequiredArgsConstructor
public class PlaceTrendQueryService {

    private static final int MAX_LIMIT = 50;

    private final PlaceTrendQueryRepository placeTrendQueryRepository;
    private final MapBookmarkTrendTrackingRepository trackingRepository;
    private final Clock clock;

    /**
     * 북마크 추적 시작일과 요청 기간 시작 중 늦은 시각부터 현재까지 순증한 공개·운영 장소의 전국 순위를 반환합니다.
     * 기간 누락은 WEEK이며 추적 기준 행이 없으면 실패합니다. REPEATABLE_READ에서 건수·목록을 조회하고 빈 목록의 전체 페이지는 1로 표시합니다.
     */
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
