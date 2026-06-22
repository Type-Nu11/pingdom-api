package com.typenull.pingdom.moderation.application.support;

import com.typenull.pingdom.place.domain.place.MapPlace;
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

@Component
public class AdminPlaceDuplicateResolver {

    private static final double DUPLICATE_DISTANCE_METERS = 50d;

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
