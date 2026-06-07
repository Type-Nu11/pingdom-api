package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.MapBookmarkRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceRecommendationSimilarityService {

    private static final double EARTH_RADIUS_METERS = 6_371_000d;
    private static final double GEO_SIMILARITY_DECAY_METERS = 2_500d;

    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapImageLikeRepository mapImageLikeRepository;

    public SimilarityContext buildContext(Collection<Long> placeIds, Map<Long, MapPlace> placeIndex) {
        Map<Long, Set<Long>> bookmarkUsersByPlace = new HashMap<>();
        Map<Long, Set<Long>> likeUsersByPlace = new HashMap<>();

        if (!placeIds.isEmpty()) {
            for (MapBookmarkRepository.PlaceBookmarkUserProjection projection :
                    mapBookmarkRepository.findBookmarkUsersByPlaceIds(placeIds)) {
                bookmarkUsersByPlace
                        .computeIfAbsent(projection.getPlaceId(), ignored -> new HashSet<>())
                        .add(projection.getUserId());
            }

            for (MapImageLikeRepository.PlaceLikeUserProjection projection :
                    mapImageLikeRepository.findLikeUsersByPlaceIds(placeIds)) {
                likeUsersByPlace
                        .computeIfAbsent(projection.getPlaceId(), ignored -> new HashSet<>())
                        .add(projection.getUserId());
            }
        }

        return new SimilarityContext(placeIndex, bookmarkUsersByPlace, likeUsersByPlace);
    }

    public double similarity(Long leftPlaceId, Long rightPlaceId, SimilarityContext context) {
        if (Objects.equals(leftPlaceId, rightPlaceId)) {
            return 1.0d;
        }

        MapPlace leftPlace = context.placeIndex().get(leftPlaceId);
        MapPlace rightPlace = context.placeIndex().get(rightPlaceId);

        if (leftPlace == null || rightPlace == null) {
            return 0d;
        }

        double geoSimilarity = geoSimilarity(leftPlace, rightPlace);
        double bookmarkSimilarity = jaccard(
                context.bookmarkUsersByPlace().get(leftPlaceId),
                context.bookmarkUsersByPlace().get(rightPlaceId)
        );
        double likeSimilarity = jaccard(
                context.likeUsersByPlace().get(leftPlaceId),
                context.likeUsersByPlace().get(rightPlaceId)
        );

        return (0.55d * geoSimilarity)
                + (0.30d * bookmarkSimilarity)
                + (0.15d * likeSimilarity);
    }

    private double geoSimilarity(MapPlace leftPlace, MapPlace rightPlace) {
        if (leftPlace.getLatitude() == null || leftPlace.getLongitude() == null
                || rightPlace.getLatitude() == null || rightPlace.getLongitude() == null) {
            return 0d;
        }

        double distanceMeters = calculateDistanceMeters(
                leftPlace.getLatitude(),
                leftPlace.getLongitude(),
                rightPlace.getLatitude(),
                rightPlace.getLongitude()
        );

        return Math.exp(-distanceMeters / GEO_SIMILARITY_DECAY_METERS);
    }

    private double jaccard(Set<Long> left, Set<Long> right) {
        if (left == null || left.isEmpty() || right == null || right.isEmpty()) {
            return 0d;
        }

        Set<Long> smaller = left.size() <= right.size() ? left : right;
        Set<Long> larger = smaller == left ? right : left;

        long intersectionSize = 0L;
        for (Long value : smaller) {
            if (larger.contains(value)) {
                intersectionSize++;
            }
        }

        if (intersectionSize == 0L) {
            return 0d;
        }

        long unionSize = left.size() + right.size() - intersectionSize;
        return (double) intersectionSize / (double) unionSize;
    }

    private double calculateDistanceMeters(
            double baseLatitude,
            double baseLongitude,
            double targetLatitude,
            double targetLongitude
    ) {
        double latitudeDelta = Math.toRadians(targetLatitude - baseLatitude);
        double longitudeDelta = Math.toRadians(targetLongitude - baseLongitude);
        double baseLatitudeRadians = Math.toRadians(baseLatitude);
        double targetLatitudeRadians = Math.toRadians(targetLatitude);

        double a = Math.sin(latitudeDelta / 2d) * Math.sin(latitudeDelta / 2d)
                + Math.cos(baseLatitudeRadians) * Math.cos(targetLatitudeRadians)
                * Math.sin(longitudeDelta / 2d) * Math.sin(longitudeDelta / 2d);
        double c = 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
        return EARTH_RADIUS_METERS * c;
    }

    public record SimilarityContext(
            Map<Long, MapPlace> placeIndex,
            Map<Long, Set<Long>> bookmarkUsersByPlace,
            Map<Long, Set<Long>> likeUsersByPlace
    ) {
    }
}
