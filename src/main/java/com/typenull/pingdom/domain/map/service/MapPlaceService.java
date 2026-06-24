package com.typenull.pingdom.domain.map.service;

import com.typenull.pingdom.domain.map.domain.MapPlace;
import com.typenull.pingdom.domain.map.dto.PlaceCreateRequest;
import com.typenull.pingdom.domain.map.dto.PlaceCreateResponse;
import com.typenull.pingdom.domain.map.exception.MapErrorCode;
import com.typenull.pingdom.domain.map.exception.MapException;
import com.typenull.pingdom.domain.map.repository.MapPlaceRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MapPlaceService {

    private final MapPlaceRepository mapPlaceRepository;
    private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

    @Transactional
    public PlaceCreateResponse createPlace(PlaceCreateRequest request, long userId) {
        Point location = toPoint(request.latitude(), request.longitude());
        MapPlace mapPlace = MapPlace.builder()
                .name(request.name())
                .address(request.address())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .location(location)
                .userId(userId)
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
