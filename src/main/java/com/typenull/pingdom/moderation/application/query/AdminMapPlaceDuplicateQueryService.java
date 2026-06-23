package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateCandidateItem;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateGroupItem;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateResponse;
import com.typenull.pingdom.moderation.application.support.AdminPlaceDuplicateResolver;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceDuplicateQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMapPlaceDuplicateQueryService {

    private static final double DUPLICATE_DISTANCE_METERS = 50d;
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    private final MapPlaceRepository mapPlaceRepository;
    private final MapPlaceDuplicateQueryRepository mapPlaceDuplicateQueryRepository;
    private final AdminPlaceDuplicateResolver adminPlaceDuplicateResolver;

    @Transactional(readOnly = true)
    public AdminMapPlaceDuplicateResponse listDuplicatePlaces() {
        AdminPlaceDuplicateResolver.DuplicateAnalysis duplicateAnalysis =
                adminPlaceDuplicateResolver.analyze(mapPlaceDuplicateQueryRepository.findPotentialDuplicatePlaces());

        List<AdminMapPlaceDuplicateGroupItem> groups = duplicateAnalysis.groups().stream()
                .map(group -> new AdminMapPlaceDuplicateGroupItem(
                        group.representativePlaceId(),
                        group.memberPlaceIds(),
                        group.reasons()
                ))
                .toList();

        return new AdminMapPlaceDuplicateResponse(groups, groups.size());
    }

    @Transactional(readOnly = true)
    public AdminMapPlaceDuplicateDetailResponse getDuplicatePlace(Long placeId) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_DUPLICATE_NOT_FOUND));

        Map<Long, MapPlace> candidatePlacesById = new LinkedHashMap<>();
        candidatePlacesById.put(mapPlace.getId(), mapPlace);

        if (mapPlace.getKakaoPlaceId() != null && !mapPlace.getKakaoPlaceId().trim().isEmpty()) {
            mapPlaceDuplicateQueryRepository.findDuplicateCandidatesByKakaoPlaceId(placeId, mapPlace.getKakaoPlaceId())
                    .forEach(candidatePlace -> candidatePlacesById.put(candidatePlace.getId(), candidatePlace));
        }

        if (hasCoordinates(mapPlace)) {
            double latitudeDelta = Math.toDegrees(DUPLICATE_DISTANCE_METERS / EARTH_RADIUS_METERS);
            double longitudeDelta = calculateLongitudeDelta(mapPlace.getLatitude(), DUPLICATE_DISTANCE_METERS);
            mapPlaceDuplicateQueryRepository.findDuplicateCandidatesByNameAndAddressInBoundingBox(
                            placeId,
                            mapPlace.getName(),
                            mapPlace.getAddress(),
                            mapPlace.getLatitude() - latitudeDelta,
                            mapPlace.getLatitude() + latitudeDelta,
                            mapPlace.getLongitude() - longitudeDelta,
                            mapPlace.getLongitude() + longitudeDelta
                    ).stream()
                    .forEach(candidatePlace -> candidatePlacesById.put(candidatePlace.getId(), candidatePlace));
        }

        AdminPlaceDuplicateResolver.DuplicateAnalysis duplicateAnalysis =
                adminPlaceDuplicateResolver.analyze(candidatePlacesById.values());
        List<AdminMapPlaceDuplicateCandidateItem> candidates = duplicateAnalysis.candidatesOf(placeId).stream()
                .map(candidate -> {
                    MapPlace candidatePlace = candidatePlacesById.get(candidate.placeId());
                    if (candidatePlace == null) {
                        throw new AdminException(AdminErrorCode.PLACE_DUPLICATE_NOT_FOUND);
                    }
                    return new AdminMapPlaceDuplicateCandidateItem(
                            candidatePlace.getId(),
                            candidatePlace.getName(),
                            candidatePlace.getAddress(),
                            candidatePlace.getKakaoPlaceId(),
                            candidatePlace.getLatitude(),
                            candidatePlace.getLongitude(),
                            candidatePlace.getUserId(),
                            candidatePlace.getRegistrant(),
                            candidatePlace.currentPhotoCount(),
                            candidate.reason(),
                            candidate.distanceMeters()
                    );
                })
                .toList();

        if (candidates.isEmpty()) {
            throw new AdminException(AdminErrorCode.PLACE_DUPLICATE_NOT_FOUND);
        }

        return new AdminMapPlaceDuplicateDetailResponse(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getAddress(),
                mapPlace.getKakaoPlaceId(),
                mapPlace.getLatitude(),
                mapPlace.getLongitude(),
                mapPlace.getUserId(),
                mapPlace.getRegistrant(),
                mapPlace.currentPhotoCount(),
                candidates
        );
    }

    private double calculateLongitudeDelta(double latitude, double distanceMeters) {
        double cosLatitude = Math.cos(Math.toRadians(latitude));
        if (Math.abs(cosLatitude) < 1e-12) {
            return 180d;
        }
        return Math.toDegrees(distanceMeters / (EARTH_RADIUS_METERS * cosLatitude));
    }

    private boolean hasCoordinates(MapPlace mapPlace) {
        return mapPlace.getLatitude() != null && mapPlace.getLongitude() != null;
    }
}
