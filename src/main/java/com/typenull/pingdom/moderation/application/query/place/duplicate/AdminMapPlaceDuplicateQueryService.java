package com.typenull.pingdom.moderation.application.query.place.duplicate;

import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateCandidateItem;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateGroupItem;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateResponse;
import com.typenull.pingdom.moderation.application.support.AdminPlaceDuplicateResolver;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceDuplicateQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB에서 중복 가능 장소를 좁힌 뒤 메모리의 판별기로 실제 후보·연결 그룹을 계산.
 * 목록 페이지는 전체 그룹 계산 이후 잘라내며, 상세는 Kakao ID 또는 이름·주소·50m 경계 상자로 후보를 획득.
 * 장소가 있어도 판별된 상대 후보가 없으면 중복 후보 없음 오류를 반환.
 */
@Service
@RequiredArgsConstructor
public class AdminMapPlaceDuplicateQueryService {

    private static final double DUPLICATE_DISTANCE_METERS = 50d;
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    private final MapPlaceRepository mapPlaceRepository;
    private final MapPlaceDuplicateQueryRepository mapPlaceDuplicateQueryRepository;
    private final AdminPlaceDuplicateResolver adminPlaceDuplicateResolver;

    /**
     * DB의 중복 가능 장소 전체를 판별해 연결 그룹을 만든 뒤 요청 페이지를 메모리에서 잘라 반환.
     * page는 1 이상·limit는 1~100으로 보정하며 총건수는 장소 수가 아닌 그룹 수.
     */
    @Transactional(readOnly = true)
    public AdminMapPlaceDuplicateResponse listDuplicatePlaces(int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        AdminPlaceDuplicateResolver.DuplicateAnalysis duplicateAnalysis =
                adminPlaceDuplicateResolver.analyze(mapPlaceDuplicateQueryRepository.findPotentialDuplicatePlaces());

        List<AdminMapPlaceDuplicateGroupItem> groups = duplicateAnalysis.groups().stream()
                .map(group -> new AdminMapPlaceDuplicateGroupItem(
                        group.representativePlaceId(),
                        group.memberPlaceIds(),
                        group.reasons()
                ))
                .toList();

        int total = groups.size();
        int fromIndex = Math.min((safePage - 1) * safeLimit, total);
        int toIndex = Math.min(fromIndex + safeLimit, total);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeLimit);
        return new AdminMapPlaceDuplicateResponse(
                groups.subList(fromIndex, toIndex), safePage, safeLimit, total, totalPages, safePage < totalPages);
    }

    /**
     * 장소의 Kakao ID 또는 이름·주소·좌표 경계로 후보를 모으고 판별기가 인정한 상대 장소와 중복 근거를 반환.
     * 요청 장소가 없거나 판별 후 상대 후보가 없으면 PLACE_DUPLICATE_NOT_FOUND로 거절.
     */
    @Transactional(readOnly = true)
    public AdminMapPlaceDuplicateDetailResponse getDuplicatePlace(Long placeId) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_DUPLICATE_NOT_FOUND));

        Map<Long, MapPlace> candidatePlacesById = new LinkedHashMap<>();
        candidatePlacesById.put(mapPlace.getId(), mapPlace);

        String kakaoPlaceId = trimToNull(mapPlace.getKakaoPlaceId());
        if (kakaoPlaceId != null) {
            mapPlaceDuplicateQueryRepository.findDuplicateCandidatesByKakaoPlaceId(placeId, kakaoPlaceId)
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
