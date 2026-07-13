package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.api.dto.coordinate.PlaceCoordinateCreateResponse;
import com.typenull.pingdom.place.api.dto.place.PlaceCreateResponse;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.place.GeocodingSource;
import com.typenull.pingdom.place.domain.place.PlaceCategoryPolicy;
import com.typenull.pingdom.place.domain.place.TouristCategory;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.support.PlaceCoordinateTokenStore;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MapPlaceService {

    private final MapPlaceRepository mapPlaceRepository;
    private final UserRepository userRepository;
    private final PlaceCoordinateTokenStore placeCoordinateTokenStore;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

    public PlaceCoordinateCreateResponse createCoordinateToken(
            double baseLatitude,
            double baseLongitude,
            String kakaoPlaceId,
            long userId
    ) {
        double finalLatitude = baseLatitude;
        double finalLongitude = baseLongitude;
        String normalizedKakaoPlaceId = trimToNull(kakaoPlaceId);
        String token = placeCoordinateTokenStore.putUserPin(
                userId,
                normalizedKakaoPlaceId,
                finalLatitude,
                finalLongitude
        );
        return new PlaceCoordinateCreateResponse(token, normalizedKakaoPlaceId);
    }

    public PlaceCoordinateTokenStore.Entry peekCoordinateToken(String coordinateToken) {
        return placeCoordinateTokenStore.peek(coordinateToken);
    }

    @Transactional
    public PlaceCreateResponse uploadPlaceByToken(
            String kakaoPlaceId,
            String name,
            String address,
            String category,
            String imageUrl,
            String coordinateToken,
            long userId
    ) {
        return uploadPlaceByToken(
                kakaoPlaceId,
                name,
                address,
                null,
                null,
                null,
                category,
                imageUrl,
                null,
                null,
                null,
                coordinateToken,
                userId
        );
    }

    @Transactional
    public PlaceCreateResponse uploadPlaceByToken(
            String kakaoPlaceId,
            String name,
            String address,
            String roadAddress,
            String jibunAddress,
            String postalCode,
            String category,
            String imageUrl,
            String englishName,
            String touristSummary,
            Set<TouristCategory> touristCategories,
            String coordinateToken,
            long userId
    ) {
        String username = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND))
                .getUsername();
        String normalizedKakaoPlaceId = trimToNull(kakaoPlaceId);
        PlaceCoordinateTokenStore.Entry entry = placeCoordinateTokenStore.consume(coordinateToken);
        if (entry == null || entry.userId() != userId) {
            throw new MapException(MapErrorCode.PLACE_COORDINATE_TOKEN_INVALID);
        }

        String tokenKakaoPlaceId = entry.kakaoPlaceId();
        if (!Objects.equals(normalizedKakaoPlaceId, tokenKakaoPlaceId)) {
            throw new MapException(MapErrorCode.PLACE_COORDINATE_TOKEN_INVALID);
        }

        String normalizedRoadAddress = trimToNull(roadAddress);
        String normalizedJibunAddress = trimToNull(jibunAddress);
        String normalizedPostalCode = trimToNull(postalCode);
        String representativeAddress = representativeAddress(address, normalizedRoadAddress, normalizedJibunAddress);

        if (normalizedKakaoPlaceId != null) {
            if (mapPlaceRepository.existsByKakaoPlaceId(normalizedKakaoPlaceId)) {
                throw new MapException(MapErrorCode.PLACE_ALREADY_EXISTS);
            }
        } else if (mapPlaceRepository.existsByNameAndAddressAndLatitudeAndLongitude(
                name,
                representativeAddress,
                entry.latitude(),
                entry.longitude()
        )) {
            throw new MapException(MapErrorCode.PLACE_ALREADY_EXISTS);
        }

        Point location = toPoint(entry.latitude(), entry.longitude());
        String normalizedEnglishName = trimToNull(englishName);
        String normalizedTouristSummary = trimToNull(touristSummary);
        Set<TouristCategory> normalizedTouristCategories = normalizeTouristCategories(touristCategories);
        GeocodingSource geocodingSource = entry.geocodingSource();
        MapPlace mapPlace = MapPlace.builder()
                .kakaoPlaceId(normalizedKakaoPlaceId)
                .name(name)
                .address(representativeAddress)
                .roadAddress(normalizedRoadAddress)
                .jibunAddress(normalizedJibunAddress)
                .postalCode(normalizedPostalCode)
                .geocodingSource(geocodingSource)
                .category(PlaceCategoryPolicy.normalize(category))
                .imageUrl(trimToNull(imageUrl))
                .latitude(entry.latitude())
                .longitude(entry.longitude())
                .location(location)
                .userId(userId)
                .registrant(username)
                .build();
        mapPlace.updateTouristInformation(
                normalizedEnglishName,
                normalizedTouristSummary,
                normalizedTouristCategories
        );

        MapPlace saved = mapPlaceRepository.save(mapPlace);
        placeRecommendationSnapshotService.initialize(saved.getId());
        return new PlaceCreateResponse(
                saved.getId(),
                saved.getName(),
                saved.getEnglishName(),
                saved.getAddress(),
                saved.getRoadAddress(),
                saved.getJibunAddress(),
                saved.getPostalCode(),
                saved.getGeocodingSource(),
                saved.getOperatingStatus(),
                saved.getOperatingStatusCheckedAt(),
                saved.getTouristSummary(),
                saved.currentTouristCategories(),
                saved.getLatitude(),
                saved.getLongitude()
        );
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String representativeAddress(String address, String roadAddress, String jibunAddress) {
        if (roadAddress != null) {
            return roadAddress;
        }
        if (jibunAddress != null) {
            return jibunAddress;
        }
        return StringUtils.hasText(address) ? address.trim() : address;
    }

    private Set<TouristCategory> normalizeTouristCategories(Set<TouristCategory> touristCategories) {
        if (touristCategories == null || touristCategories.isEmpty()) {
            return EnumSet.noneOf(TouristCategory.class);
        }
        return EnumSet.copyOf(touristCategories);
    }

    @Transactional
    public void deletePlace(long placeId, long userId) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));

        if (!Objects.equals(mapPlace.getUserId(), userId)) {
            throw new MapException(MapErrorCode.OTHERS_PLACE_NOT_DELETED);
        }

        mapPlaceRepository.delete(mapPlace);
        placeRecommendationSnapshotService.delete(placeId);
    }

    private static Point toPoint(double latitude, double longitude) {
        // PostGIS uses (x=longitude, y=latitude) for WGS84 points.
        return WGS84.createPoint(new Coordinate(longitude, latitude));
    }
}
