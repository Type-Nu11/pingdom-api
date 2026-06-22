package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.api.dto.place.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.place.PlaceListItem;
import com.typenull.pingdom.place.api.dto.place.PlaceListResponse;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository.PlaceSearchProjection;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PlaceQueryServiceImpl implements PlaceQueryService {

    private static final int MAX_SEARCH_LIMIT = 100;
    private static final double KM_PER_LATITUDE_DEGREE = 111.32d;
    private static final double MIN_COSINE_FOR_LONGITUDE_DELTA = 0.000001d;

    private final MapPlaceRepository mapPlaceRepository;

    @Override
    @Transactional(readOnly = true)
    public PlaceListResponse listPlaces(PlaceSearchCondition condition) {
        if (condition == null) {
            throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
        }

        int safePage = Math.max(condition.page(), 1);
        int safeLimit = Math.max(1, Math.min(condition.limit(), MAX_SEARCH_LIMIT));
        Pageable pageable = PageRequest.of(safePage - 1, safeLimit);
        PlaceSearchSort searchSort = PlaceSearchSort.from(condition.sort());
        LocationSearch locationSearch = LocationSearch.from(
                condition.latitude(),
                condition.longitude(),
                condition.radiusKm()
        );
        if (searchSort == PlaceSearchSort.NEAREST && !locationSearch.enabled()) {
            throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
        }

        Page<PlaceSearchProjection> placePage = mapPlaceRepository.searchPlaces(
                toLikePattern(condition.keyword()),
                normalizeCategory(condition.category()),
                locationSearch.enabled(),
                locationSearch.latitude(),
                locationSearch.longitude(),
                locationSearch.radiusMeters(),
                locationSearch.minLatitude(),
                locationSearch.maxLatitude(),
                locationSearch.westLongitude(),
                locationSearch.eastLongitude(),
                locationSearch.longitudeWrapped(),
                searchSort.name(),
                pageable
        );

        List<PlaceListItem> places = placePage.getContent()
                .stream()
                .map(this::toListItem)
                .toList();

        return PlaceListResponse.of(
                places,
                safePage,
                safeLimit,
                placePage.getTotalElements(),
                placePage.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PlaceDetailResponse getPlace(Long placeId) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));

        return new PlaceDetailResponse(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getAddress(),
                mapPlace.getLatitude(),
                mapPlace.getLongitude(),
                mapPlace.getRegistrant()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PlaceListResponse listBookmarkedPlaces(Long userId, int page, int limit) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        Pageable pageable = PageRequest.of(safePage - 1, safeLimit);

        Page<MapPlace> placePage = mapPlaceRepository.findBookmarkedPlacesByUserId(userId, pageable);
        List<PlaceListItem> places = placePage.getContent().stream()
                .map(this::toListItem)
                .toList();

        return PlaceListResponse.of(
                places,
                safePage,
                safeLimit,
                placePage.getTotalElements(),
                placePage.getTotalPages()
        );
    }

    private PlaceListItem toListItem(MapPlace mapPlace) {
        return new PlaceListItem(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getAddress(),
                mapPlace.getCategory(),
                mapPlace.getLatitude(),
                mapPlace.getLongitude(),
                null
        );
    }

    private PlaceListItem toListItem(PlaceSearchProjection projection) {
        Double distanceMeters = projection.getDistanceMeters();
        return new PlaceListItem(
                projection.getId(),
                projection.getName(),
                projection.getAddress(),
                projection.getCategory(),
                projection.getLatitude(),
                projection.getLongitude(),
                distanceMeters == null ? null : Math.round(distanceMeters)
        );
    }

    private String toLikePattern(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return "%" + escapeLike(normalized.toLowerCase(Locale.ROOT)) + "%";
    }

    private String normalizeCategory(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private record LocationSearch(
            boolean enabled,
            Double latitude,
            Double longitude,
            Double radiusMeters,
            Double minLatitude,
            Double maxLatitude,
            Double westLongitude,
            Double eastLongitude,
            boolean longitudeWrapped
    ) {

        static LocationSearch from(Double latitude, Double longitude, Double radiusKm) {
            boolean hasLatitude = latitude != null;
            boolean hasLongitude = longitude != null;
            boolean hasRadius = radiusKm != null;
            if (hasLatitude != hasLongitude || hasLatitude != hasRadius) {
                throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
            }
            if (!hasLatitude) {
                return new LocationSearch(false, null, null, null, null, null, null, null, false);
            }
            if (radiusKm <= 0d) {
                throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
            }

            double latitudeDelta = radiusKm / KM_PER_LATITUDE_DEGREE;
            double minLatitude = Math.max(-90d, latitude - latitudeDelta);
            double maxLatitude = Math.min(90d, latitude + latitudeDelta);
            double longitudeDelta = toLongitudeDelta(latitude, radiusKm);
            LongitudeBounds longitudeBounds = LongitudeBounds.from(longitude, longitudeDelta);

            return new LocationSearch(
                    true,
                    latitude,
                    longitude,
                    radiusKm * 1_000d,
                    minLatitude,
                    maxLatitude,
                    longitudeBounds.westLongitude(),
                    longitudeBounds.eastLongitude(),
                    longitudeBounds.wrapped()
            );
        }

        private static double toLongitudeDelta(double latitude, double radiusKm) {
            double cosine = Math.cos(Math.toRadians(latitude));
            if (Math.abs(cosine) < MIN_COSINE_FOR_LONGITUDE_DELTA) {
                return 180d;
            }
            return Math.min(180d, radiusKm / (KM_PER_LATITUDE_DEGREE * Math.abs(cosine)));
        }
    }

    private record LongitudeBounds(double westLongitude, double eastLongitude, boolean wrapped) {

        static LongitudeBounds from(double longitude, double longitudeDelta) {
            if (longitudeDelta >= 180d) {
                return new LongitudeBounds(-180d, 180d, false);
            }

            double westLongitude = normalizeLongitude(longitude - longitudeDelta);
            double eastLongitude = normalizeLongitude(longitude + longitudeDelta);
            return new LongitudeBounds(westLongitude, eastLongitude, westLongitude > eastLongitude);
        }

        private static double normalizeLongitude(double longitude) {
            double normalized = longitude;
            while (normalized < -180d) {
                normalized += 360d;
            }
            while (normalized > 180d) {
                normalized -= 360d;
            }
            return normalized;
        }
    }

}
