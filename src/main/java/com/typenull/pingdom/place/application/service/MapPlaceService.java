package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.api.dto.PlaceCreateRequest;
import com.typenull.pingdom.place.api.dto.PlaceCreateResponse;
import com.typenull.pingdom.place.api.dto.PlaceCoordinateCreateResponse;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.support.PlaceCoordinateTokenStore;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.Objects;
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
    private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

    @Transactional
    public PlaceCreateResponse createPlace(PlaceCreateRequest request, long userId) {
        Point location = toPoint(request.latitude(), request.longitude());
        String username = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND))
                .getUsername();
        MapPlace mapPlace = MapPlace.builder()
                .name(request.name())
                .address(request.address())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .location(location)
                .userId(userId)
                .registrant(username)
                .build();

        MapPlace saved = mapPlaceRepository.save(mapPlace);
        return new PlaceCreateResponse(
                saved.getId(),
                saved.getName(),
                saved.getAddress(),
                saved.getLatitude(),
                saved.getLongitude()
        );
    }

    public PlaceCoordinateCreateResponse createCoordinateToken(double baseLatitude, double baseLongitude, long userId) {
        // TODO: ±a 오차 적용 로직은 별도 이슈에서 구현 예정 (현재는 기준 좌표를 그대로 사용)
        double finalLatitude = baseLatitude;
        double finalLongitude = baseLongitude;
        String token = placeCoordinateTokenStore.put(userId, finalLatitude, finalLongitude);
        return new PlaceCoordinateCreateResponse(token, finalLatitude, finalLongitude);
    }

    @Transactional
    public PlaceCreateResponse uploadPlaceByToken(
            String kakaoPlaceId,
            String name,
            String address,
            String coordinateToken,
            long userId
    ) {
        String username = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND))
                .getUsername();
        String normalizedKakaoPlaceId = normalizeKakaoPlaceId(kakaoPlaceId);
        if (normalizedKakaoPlaceId != null && mapPlaceRepository.existsByKakaoPlaceId(normalizedKakaoPlaceId)) {
            throw new MapException(MapErrorCode.PLACE_ALREADY_EXISTS);
        }

        PlaceCoordinateTokenStore.Entry entry = placeCoordinateTokenStore.consume(coordinateToken);
        if (entry == null || entry.userId() != userId) {
            throw new MapException(MapErrorCode.PLACE_COORDINATE_TOKEN_INVALID);
        }

        Point location = toPoint(entry.latitude(), entry.longitude());
        MapPlace mapPlace = MapPlace.builder()
                .kakaoPlaceId(normalizedKakaoPlaceId)
                .name(name)
                .address(address)
                .latitude(entry.latitude())
                .longitude(entry.longitude())
                .location(location)
                .userId(userId)
                .registrant(username)
                .build();

        MapPlace saved = mapPlaceRepository.save(mapPlace);
        return new PlaceCreateResponse(
                saved.getId(),
                saved.getName(),
                saved.getAddress(),
                saved.getLatitude(),
                saved.getLongitude()
        );
    }

    private String normalizeKakaoPlaceId(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @Transactional
    public void deletePlace(long placeId, long userId) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));

        if (!Objects.equals(mapPlace.getUserId(), userId)) {
            throw new MapException(MapErrorCode.OTHERS_PLACE_NOT_DELETED);
        }

        mapPlaceRepository.delete(mapPlace);
    }

    private static Point toPoint(double latitude, double longitude) {
        // PostGIS uses (x=longitude, y=latitude) for WGS84 points.
        return WGS84.createPoint(new Coordinate(longitude, latitude));
    }
}
