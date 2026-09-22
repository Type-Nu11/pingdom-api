package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.api.dto.ranking.PlaceRankingPeriod;
import com.typenull.pingdom.place.api.dto.ranking.PlaceRankingResponse;
import com.typenull.pingdom.place.api.dto.ranking.PlaceRankingScope;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceRankingQueryRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 최근 게시물 좋아요 집계를 장소 순위로 변환하고 요청 회원의 북마크 여부를 덧붙입니다.
 * LOCAL 후보가 한 페이지보다 적으면 요청 반경을 최대 50km로 확장하며 페이지 크기는 50개로 제한합니다.
 */
@Service
@RequiredArgsConstructor
public class PlaceRankingQueryService {
    private static final double DEFAULT_RADIUS = 5.0;
    private static final double MAX_RADIUS = 50.0;

    private final MapBookmarkRepository bookmarkRepository;
    private final PlaceRankingQueryRepository rankingQueryRepository;

    /**
     * 기간 내 게시물 좋아요를 집계한 장소 순위와 선택적 사용자 북마크 여부를 반환합니다. 기간 누락은 최근 7일입니다.
     * LOCAL은 좌표 쌍을 요구하고 반경은 기본 5km·최대 50km이며 후보 수가 페이지 크기보다 적으면 50km로 확대합니다.
     * 페이지 크기는 1~50이고 전국 조회의 반경 응답은 null입니다. 건수와 목록을 하나의 명시적 서비스 트랜잭션으로 묶지는 않습니다.
     */
    public PlaceRankingResponse find(
            PlaceRankingScope scope,
            Double latitude,
            Double longitude,
            Double radiusKm,
            PlaceRankingPeriod period,
            String category,
            int page,
            int limit,
            Long userId
    ) {
        if (scope == PlaceRankingScope.LOCAL && (latitude == null || longitude == null)) {
            throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
        }
        if (radiusKm != null && (radiusKm <= 0 || radiusKm > MAX_RADIUS)) {
            throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
        }

        int safePage = Math.max(1, page);
        int safeLimit = Math.min(50, Math.max(1, limit));
        PlaceRankingPeriod safePeriod = period == null ? PlaceRankingPeriod.WEEK : period;
        Instant end = Instant.now();
        Instant start = switch (safePeriod) {
            case DAY -> end.minus(1, java.time.temporal.ChronoUnit.DAYS);
            case WEEK -> end.minus(7, java.time.temporal.ChronoUnit.DAYS);
            case MONTH -> end.minus(30, java.time.temporal.ChronoUnit.DAYS);
        };
        boolean local = scope == PlaceRankingScope.LOCAL;
        double requestedRadiusKm = radiusKm == null ? DEFAULT_RADIUS : radiusKm;
        String normalizedCategory = category == null || category.isBlank()
                ? null
                : category.toLowerCase(Locale.ROOT);
        LocalDateTime periodStart = LocalDateTime.ofInstant(start, ZoneOffset.UTC);

        boolean expanded = false;
        double appliedRadiusKm = requestedRadiusKm;
        long total = countRankedPlaces(
                local,
                requestedRadiusKm,
                latitude,
                longitude,
                periodStart,
                normalizedCategory
        );
        if (local && total < safeLimit && requestedRadiusKm < MAX_RADIUS) {
            expanded = true;
            appliedRadiusKm = MAX_RADIUS;
            total = countRankedPlaces(
                    true,
                    appliedRadiusKm,
                    latitude,
                    longitude,
                    periodStart,
                    normalizedCategory
            );
        }
        List<PlaceRankingQueryRepository.PlaceRankingProjection> rankings = rankingQueryRepository.findRankedPlaces(
                periodStart,
                normalizedCategory,
                local,
                latitude,
                longitude,
                appliedRadiusKm * 1_000,
                PageRequest.of(safePage - 1, safeLimit)
        );

        Set<Long> bookmarked = userId == null || rankings.isEmpty()
                ? Set.of()
                : bookmarkRepository.findPlaceIdsByUserIdAndPlaceIds(
                        userId,
                        rankings.stream().map(PlaceRankingQueryRepository.PlaceRankingProjection::getPlaceId).toList()
                );
        int pageOffset = (safePage - 1) * safeLimit;
        List<PlaceRankingResponse.Item> items = java.util.stream.IntStream.range(0, rankings.size())
                .mapToObj(index -> toItem(rankings.get(index), pageOffset + index + 1, local, userId, bookmarked))
                .toList();
        int totalPages = (int) Math.ceil(total / (double) safeLimit);

        return new PlaceRankingResponse(
                scope,
                safePeriod,
                start,
                end,
                "POST_LIKE_COUNT",
                end,
                local ? requestedRadiusKm : null,
                local ? appliedRadiusKm : null,
                expanded,
                items,
                safePage,
                safeLimit,
                total,
                totalPages,
                safePage < totalPages
        );
    }

    private long countRankedPlaces(
            boolean local,
            double radiusKm,
            Double latitude,
            Double longitude,
            LocalDateTime periodStart,
            String category
    ) {
        return rankingQueryRepository.countRankedPlaces(
                periodStart,
                category,
                local,
                latitude,
                longitude,
                radiusKm * 1_000
        );
    }

    private PlaceRankingResponse.Item toItem(
            PlaceRankingQueryRepository.PlaceRankingProjection ranking,
            int rank,
            boolean local,
            Long userId,
            Set<Long> bookmarked
    ) {
        Long distanceMeters = local && ranking.getDistanceMeters() != null
                ? Math.round(ranking.getDistanceMeters())
                : null;
        return new PlaceRankingResponse.Item(
                rank,
                ranking.getPlaceId(),
                ranking.getPlaceName(),
                ranking.getCategory(),
                ranking.getLatitude(),
                ranking.getLongitude(),
                distanceMeters,
                ranking.getLikeCount().doubleValue(),
                ranking.getLikeCount(),
                ranking.getPostCount(),
                ranking.getImageUrl(),
                ranking.getThumbnailUrl(),
                "POST",
                ranking.getRepresentativePostId(),
                null,
                ranking.getRegistrantUsername(),
                userId == null ? null : bookmarked.contains(ranking.getPlaceId())
        );
    }
}
