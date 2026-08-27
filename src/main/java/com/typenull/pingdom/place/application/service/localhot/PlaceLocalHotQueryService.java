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

@Service
@RequiredArgsConstructor
public class PlaceLocalHotQueryService {

    private static final int MAX_LIMIT = 50;

    private final PlaceAdministrativeRegionResolver regionResolver;
    private final PlaceAdministrativeRegionRepository regionRepository;
    private final PlaceLocalHotQueryRepository localHotQueryRepository;

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
