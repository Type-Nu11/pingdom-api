package com.typenull.pingdom.place.application.service.localhot;

import com.typenull.pingdom.place.api.dto.localhot.PlaceLocalHotQuery;
import com.typenull.pingdom.place.api.dto.localhot.PlaceLocalHotResponse;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegion;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegionResolver;
import com.typenull.pingdom.place.domain.place.region.ResolvedPlaceAdministrativeRegion;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceAdministrativeRegionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceLocalHotQueryRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좌표 또는 저장된 행정구역 코드 하나로 지역 인기 장소를 조회.
 * 반복 읽기 트랜잭션에서 개수·목록을 계산하고 회원 북마크 상태와 페이지 기준 순위를 조합.
 */
@Service
@RequiredArgsConstructor
public class PlaceLocalHotQueryService {

    private static final int MAX_LIMIT = 50;

    private final PlaceAdministrativeRegionResolver regionResolver;
    private final PlaceAdministrativeRegionRepository regionRepository;
    private final PlaceLocalHotQueryRepository localHotQueryRepository;

    /**
     * 위경도 쌍 또는 지역 코드 중 하나로 지역을 결정하고 지역 핫플 페이지와 사용자 북마크 상태를 반환.
     * 페이지를 1 이상, 크기를 허용 범위로 보정하며 순위는 페이지 오프셋을 포함하고 빈 목록의 전체 페이지는 1.
     * REPEATABLE_READ 경계에서 건수와 목록을 읽으며 잘못된 지역 조건·없는 지역은 도메인 오류로 거절.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PlaceLocalHotResponse find(PlaceLocalHotQuery query, long userId) {
        ResolvedPlaceAdministrativeRegion region = resolveRegion(query);
        int page = Math.max(query.page(), 1);
        int limit = Math.min(Math.max(query.limit(), 1), MAX_LIMIT);
        long totalElements = localHotQueryRepository.countLocalHotPlaces(region.code());
        List<PlaceLocalHotQueryRepository.PlaceLocalHotProjection> places = localHotQueryRepository.findLocalHotPlaces(
                region.code(),
                userId,
                PageRequest.of(page - 1, limit)
        );
        int totalPages = Math.max(1, (int) Math.ceil((double) totalElements / limit));
        return new PlaceLocalHotResponse(
                new PlaceLocalHotResponse.Region(region.code(), region.sido(), region.sigungu(), region.regionName()),
                java.util.stream.IntStream.range(0, places.size())
                        .mapToObj(index -> toItem(places.get(index), ((page - 1) * limit) + index + 1))
                        .toList(),
                page,
                limit,
                totalElements,
                totalPages,
                page < totalPages
        );
    }

    private ResolvedPlaceAdministrativeRegion resolveRegion(PlaceLocalHotQuery query) {
        boolean hasLatitude = query.latitude() != null;
        boolean hasLongitude = query.longitude() != null;
        boolean hasRegionCode = query.regionCode() != null && !query.regionCode().isBlank();
        if (hasLatitude != hasLongitude || (hasLatitude && hasRegionCode) || (!hasLatitude && !hasRegionCode)) {
            throw new MapException(MapErrorCode.LOCAL_HOT_QUERY_CONDITION_INVALID);
        }
        if (hasLatitude) {
            return regionResolver.resolve(query.latitude(), query.longitude());
        }
        PlaceAdministrativeRegion region = regionRepository.findById(query.regionCode().trim())
                .orElseThrow(() -> new MapException(MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND));
        return new ResolvedPlaceAdministrativeRegion(
                region.getCode(),
                region.getSido(),
                region.getSigungu(),
                region.getRegionName()
        );
    }

    private PlaceLocalHotResponse.Item toItem(
            PlaceLocalHotQueryRepository.PlaceLocalHotProjection projection,
            int rank
    ) {
        return new PlaceLocalHotResponse.Item(
                rank,
                projection.getPlaceId(),
                projection.getPlaceName(),
                projection.getCategory(),
                projection.getAddress(),
                projection.getLatitude(),
                projection.getLongitude(),
                projection.getImageUrl(),
                projection.getBookmarkCount(),
                projection.getBookmarked()
        );
    }
}
