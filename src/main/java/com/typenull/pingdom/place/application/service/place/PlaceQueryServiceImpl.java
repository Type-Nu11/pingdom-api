package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.api.dto.place.PlaceAutocompleteItem;
import com.typenull.pingdom.place.api.dto.place.PlaceAutocompleteResponse;
import com.typenull.pingdom.place.api.dto.place.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.place.PlaceListItem;
import com.typenull.pingdom.place.api.dto.place.PlaceListResponse;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.place.PlaceCategoryPolicy;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceSearchQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceSearchQueryRepository.PlaceSearchProjection;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PlaceQueryServiceImpl implements PlaceQueryService {

    private static final int MAX_SEARCH_LIMIT = 100;
    private static final double KM_PER_LATITUDE_DEGREE = 111.32d;
    private static final double MIN_COSINE_FOR_LONGITUDE_DELTA = 0.000001d;
    private static final int AUTOCOMPLETE_MIN_LENGTH = 2;
    private static final int AUTOCOMPLETE_MAX_LIMIT = 10;
    private static final int AUTOCOMPLETE_CANDIDATE_FETCH_SIZE = 100;
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceSearchQueryRepository placeSearchQueryRepository;

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

        Page<PlaceSearchProjection> placePage = placeSearchQueryRepository.searchPlaces(
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
    public PlaceAutocompleteResponse autocompletePlaces(String keyword, int limit, Double latitude, Double longitude) {
        String normalizedKeyword = normalizeAutocompleteKeyword(keyword);
        int safeLimit = Math.max(1, Math.min(limit, AUTOCOMPLETE_MAX_LIMIT));

        if (normalizedKeyword.length() < AUTOCOMPLETE_MIN_LENGTH) {
            return new PlaceAutocompleteResponse(normalizedKeyword, safeLimit, 0, List.of());
        }
        if ((latitude == null) != (longitude == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude와 longitude는 함께 전달해야 합니다.");
        }

        Pageable pageable = PageRequest.of(
                0,
                AUTOCOMPLETE_CANDIDATE_FETCH_SIZE,
                Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"))
        );

        List<PlaceAutocompleteItem> places = placeSearchQueryRepository.findAutocompleteCandidates(normalizedKeyword, pageable)
                .stream()
                .sorted((first, second) -> compareAutocompletePlaces(
                        first,
                        second,
                        normalizedKeyword,
                        latitude,
                        longitude
                ))
                .limit(safeLimit)
                .map(place -> toAutocompleteItem(place, latitude, longitude))
                .toList();

        return new PlaceAutocompleteResponse(normalizedKeyword, safeLimit, places.size(), places);
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

        Page<MapPlace> placePage = placeSearchQueryRepository.findBookmarkedPlacesByUserId(userId, pageable);
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
        String normalized = PlaceCategoryPolicy.normalize(value);
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

    private PlaceAutocompleteItem toAutocompleteItem(MapPlace mapPlace, Double latitude, Double longitude) {
        return new PlaceAutocompleteItem(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getAddress(),
                mapPlace.getCategory(),
                mapPlace.getLatitude(),
                mapPlace.getLongitude(),
                calculateDistanceMeters(latitude, longitude, mapPlace)
        );
    }

    private String normalizeAutocompleteKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        String normalizedKeyword = keyword.trim().replaceAll("\\s+", " ");
        if (normalizedKeyword.isEmpty()) {
            return "";
        }
        if (!normalizedKeyword.matches(".*[0-9A-Za-z가-힣].*")) {
            return "";
        }
        return normalizedKeyword;
    }

    private int compareAutocompletePlaces(
            MapPlace first,
            MapPlace second,
            String keyword,
            Double latitude,
            Double longitude
    ) {
        int scoreCompare = Integer.compare(
                autocompleteScore(second, keyword),
                autocompleteScore(first, keyword)
        );
        if (scoreCompare != 0) {
            return scoreCompare;
        }

        Double firstDistance = calculateDistanceMeters(latitude, longitude, first);
        Double secondDistance = calculateDistanceMeters(latitude, longitude, second);
        if (firstDistance != null || secondDistance != null) {
            if (firstDistance == null) {
                return 1;
            }
            if (secondDistance == null) {
                return -1;
            }
            int distanceCompare = Double.compare(firstDistance, secondDistance);
            if (distanceCompare != 0) {
                return distanceCompare;
            }
        }

        int nameCompare = first.getName().compareToIgnoreCase(second.getName());
        if (nameCompare != 0) {
            return nameCompare;
        }
        int addressCompare = first.getAddress().compareToIgnoreCase(second.getAddress());
        if (addressCompare != 0) {
            return addressCompare;
        }
        return Long.compare(first.getId(), second.getId());
    }

    private int autocompleteScore(MapPlace mapPlace, String keyword) {
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        String name = mapPlace.getName().toLowerCase(Locale.ROOT);
        String address = mapPlace.getAddress().toLowerCase(Locale.ROOT);
        String category = mapPlace.getCategory() == null ? "" : mapPlace.getCategory().toLowerCase(Locale.ROOT);

        if (name.equals(normalizedKeyword)) {
            return 400;
        }
        if (name.startsWith(normalizedKeyword)) {
            return 300;
        }
        if (name.contains(normalizedKeyword)) {
            return 200;
        }
        if (address.contains(normalizedKeyword)) {
            return 100;
        }
        if (category.contains(normalizedKeyword)) {
            return 50;
        }
        return 0;
    }

    private Double calculateDistanceMeters(Double latitude, Double longitude, MapPlace mapPlace) {
        if (latitude == null || longitude == null
                || mapPlace.getLatitude() == null
                || mapPlace.getLongitude() == null) {
            return null;
        }

        double latitude1 = Math.toRadians(latitude);
        double longitude1 = Math.toRadians(longitude);
        double latitude2 = Math.toRadians(mapPlace.getLatitude());
        double longitude2 = Math.toRadians(mapPlace.getLongitude());

        double deltaLatitude = latitude2 - latitude1;
        double deltaLongitude = longitude2 - longitude1;
        double a = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2)
                + Math.cos(latitude1) * Math.cos(latitude2)
                * Math.sin(deltaLongitude / 2) * Math.sin(deltaLongitude / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
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
            if (!Double.isFinite(latitude)
                    || !Double.isFinite(longitude)
                    || !Double.isFinite(radiusKm)
                    || radiusKm <= 0d) {
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
            if (!Double.isFinite(longitude)) {
                throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
            }

            double normalized = (longitude + 180d) % 360d;
            if (normalized < 0d) {
                normalized += 360d;
            }
            double shifted = normalized - 180d;
            return shifted == -180d && longitude > 0d ? 180d : shifted;
        }
    }

}
