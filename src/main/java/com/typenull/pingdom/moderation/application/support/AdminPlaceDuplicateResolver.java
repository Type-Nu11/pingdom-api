package com.typenull.pingdom.moderation.application.support;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 장소 쌍의 Kakao ID 일치 또는 정규화 이름·주소 일치와 50m 이하 거리를 중복 근거로 계산.
 * 거리 계산은 좌표 존재를 전제로 수행. 그룹은 중복 연결의 연결 성분으로, 일부 구성원 쌍은 간접 연결만 가능.
 * 입력 전체 쌍을 비교하므로 조회 단계에서 후보 규모를 줄이는 책임은 호출자에게 귀속.
 */
@Component
public class AdminPlaceDuplicateResolver {

    private static final double DUPLICATE_DISTANCE_METERS = 50d;

    /**
     * 입력 장소의 모든 ID 쌍을 비교해 DB 변경 없이 양방향 후보·중복 연결 그룹 반환.
     * 좌표가 있는 장소 입력을 전제로 Kakao ID 또는 이름·주소·50m 거리 규칙 적용.
     * 후보는 근거·거리·ID순, 그룹은 최소 ID순 정렬. 그룹에는 직접 중복인 쌍뿐 아니라 간접 연결된 쌍도 포함 가능.
     */
    public DuplicateAnalysis analyze(Collection<MapPlace> places) {
        List<MapPlace> sortedPlaces = places.stream()
                .sorted(Comparator.comparing(MapPlace::getId))
                .toList();

        Map<Long, List<DuplicateCandidate>> candidateMap = new HashMap<>();

        for (int index = 0; index < sortedPlaces.size(); index++) {
            MapPlace left = sortedPlaces.get(index);
            for (int innerIndex = index + 1; innerIndex < sortedPlaces.size(); innerIndex++) {
                MapPlace right = sortedPlaces.get(innerIndex);
                DuplicateMatch match = match(left, right);
                if (match == null) {
                    continue;
                }

                candidateMap.computeIfAbsent(left.getId(), ignored -> new ArrayList<>())
                        .add(new DuplicateCandidate(right.getId(), match.reason(), match.distanceMeters()));
                candidateMap.computeIfAbsent(right.getId(), ignored -> new ArrayList<>())
                        .add(new DuplicateCandidate(left.getId(), match.reason(), match.distanceMeters()));
            }
        }

        Map<Long, List<DuplicateCandidate>> immutableCandidateMap = new HashMap<>();
        for (Map.Entry<Long, List<DuplicateCandidate>> entry : candidateMap.entrySet()) {
            List<DuplicateCandidate> sortedCandidates = entry.getValue().stream()
                    .sorted(Comparator
                            .comparing(DuplicateCandidate::reason)
                            .thenComparing(candidate -> candidate.distanceMeters() == null ? Double.MAX_VALUE : candidate.distanceMeters())
                            .thenComparing(DuplicateCandidate::placeId))
                    .toList();
            immutableCandidateMap.put(entry.getKey(), sortedCandidates);
        }

        List<DuplicateGroup> groups = buildGroups(immutableCandidateMap);
        return new DuplicateAnalysis(immutableCandidateMap, groups);
    }

    public boolean areDuplicates(MapPlace sourcePlace, MapPlace targetPlace) {
        return match(sourcePlace, targetPlace) != null;
    }

    private DuplicateMatch match(MapPlace left, MapPlace right) {
        if (left == null || right == null || left.getId().equals(right.getId())) {
            return null;
        }

        String leftKakaoPlaceId = normalizeKakaoPlaceId(left.getKakaoPlaceId());
        String rightKakaoPlaceId = normalizeKakaoPlaceId(right.getKakaoPlaceId());
        double distanceMeters = calculateDistanceMeters(
                left.getLatitude(),
                left.getLongitude(),
                right.getLatitude(),
                right.getLongitude()
        );

        if (leftKakaoPlaceId != null && leftKakaoPlaceId.equals(rightKakaoPlaceId)) {
            return new DuplicateMatch("KAKAO_PLACE_ID", distanceMeters);
        }

        String leftName = normalizeText(left.getName());
        String rightName = normalizeText(right.getName());
        String leftAddress = normalizeText(left.getAddress());
        String rightAddress = normalizeText(right.getAddress());

        if (leftName.equals(rightName)
                && leftAddress.equals(rightAddress)
                && distanceMeters <= DUPLICATE_DISTANCE_METERS) {
            return new DuplicateMatch("NAME_ADDRESS_COORDINATE", distanceMeters);
        }

        return null;
    }

    private List<DuplicateGroup> buildGroups(Map<Long, List<DuplicateCandidate>> candidateMap) {
        Set<Long> visited = new HashSet<>();
        List<DuplicateGroup> groups = new ArrayList<>();

        for (Long startPlaceId : candidateMap.keySet().stream().sorted().toList()) {
            if (!visited.add(startPlaceId)) {
                continue;
            }

            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(startPlaceId);
            List<Long> memberPlaceIds = new ArrayList<>();
            Set<String> reasons = new LinkedHashSet<>();

            while (!queue.isEmpty()) {
                Long currentPlaceId = queue.removeFirst();
                memberPlaceIds.add(currentPlaceId);

                for (DuplicateCandidate candidate : candidateMap.getOrDefault(currentPlaceId, List.of())) {
                    reasons.add(candidate.reason());
                    if (visited.add(candidate.placeId())) {
                        queue.addLast(candidate.placeId());
                    }
                }
            }

            memberPlaceIds.sort(Long::compareTo);
            if (memberPlaceIds.size() < 2) {
                continue;
            }

            groups.add(new DuplicateGroup(
                    memberPlaceIds.get(0),
                    List.copyOf(memberPlaceIds),
                    List.copyOf(reasons)
            ));
        }

        return groups.stream()
                .sorted(Comparator.comparing(DuplicateGroup::representativePlaceId))
                .toList();
    }

    private String normalizeKakaoPlaceId(String value) {
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase()
                .replaceAll("\\s+", "");
    }

    private double calculateDistanceMeters(
            double latitude,
            double longitude,
            double otherLatitude,
            double otherLongitude
    ) {
        final double earthRadiusMeters = 6_371_000d;
        double latitudeDelta = Math.toRadians(otherLatitude - latitude);
        double longitudeDelta = Math.toRadians(otherLongitude - longitude);
        double startLatitude = Math.toRadians(latitude);
        double endLatitude = Math.toRadians(otherLatitude);

        double haversine = Math.pow(Math.sin(latitudeDelta / 2d), 2)
                + Math.cos(startLatitude) * Math.cos(endLatitude) * Math.pow(Math.sin(longitudeDelta / 2d), 2);
        double centralAngle = 2d * Math.atan2(Math.sqrt(haversine), Math.sqrt(1d - haversine));
        return earthRadiusMeters * centralAngle;
    }

    public record DuplicateAnalysis(
            Map<Long, List<DuplicateCandidate>> candidateMap,
            List<DuplicateGroup> groups
    ) {
        public List<DuplicateCandidate> candidatesOf(Long placeId) {
            return candidateMap.getOrDefault(placeId, List.of());
        }
    }

    public record DuplicateCandidate(
            Long placeId,
            String reason,
            Double distanceMeters
    ) {
    }

    public record DuplicateGroup(
            Long representativePlaceId,
            List<Long> memberPlaceIds,
            List<String> reasons
    ) {
    }

    private record DuplicateMatch(
            String reason,
            Double distanceMeters
    ) {
    }
}
