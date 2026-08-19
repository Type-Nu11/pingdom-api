package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.api.dto.ranking.PlaceRankingPeriod;
import com.typenull.pingdom.place.api.dto.ranking.PlaceRankingResponse;
import com.typenull.pingdom.place.api.dto.ranking.PlaceRankingScope;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.media.PlaceMediaPurpose;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceMediaRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaceRankingQueryService {
    private static final double DEFAULT_RADIUS = 5.0;
    private static final double MAX_RADIUS = 50.0;
    private final MapPlaceRepository placeRepository;
    private final MapImageRepository imageRepository;
    private final MapBookmarkRepository bookmarkRepository;
    private final PlaceMediaRepository mediaRepository;

    public PlaceRankingResponse find(PlaceRankingScope scope, Double latitude, Double longitude,
                                     Double radiusKm, PlaceRankingPeriod period, String category,
                                     int page, int limit, Long userId) {
        if (scope == PlaceRankingScope.LOCAL && (latitude == null || longitude == null))
            throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
        if (radiusKm != null && (radiusKm <= 0 || radiusKm > MAX_RADIUS))
            throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
        int safePage = Math.max(1, page), safeLimit = Math.min(50, Math.max(1, limit));
        PlaceRankingPeriod safePeriod = period == null ? PlaceRankingPeriod.WEEK : period;
        Instant end = Instant.now();
        Instant start = switch (safePeriod) {
            case DAY -> end.minus(1, java.time.temporal.ChronoUnit.DAYS);
            case MONTH -> end.minus(30, java.time.temporal.ChronoUnit.DAYS);
            case WEEK -> end.minus(7, java.time.temporal.ChronoUnit.DAYS);
        };
        double requested = radiusKm == null ? DEFAULT_RADIUS : radiusKm;
        List<Row> rows = new ArrayList<>();
        for (MapPlace place : placeRepository.findAll()) {
            if (category != null && !category.isBlank() && !category.equalsIgnoreCase(place.getCategory())) continue;
            double distance = distance(latitude, longitude, place.getLatitude(), place.getLongitude());
            if (scope == PlaceRankingScope.LOCAL && distance > requested) continue;
            List<MapImage> images = imageRepository.findByMapPlace_Id(place.getId()).stream()
                    .filter(i -> i.getVisibilityStatus() == MapImageVisibilityStatus.ACTIVE)
                    .filter(i -> i.getCreatedAt() != null && !i.getCreatedAt().toInstant(ZoneOffset.UTC).isBefore(start))
                    .toList();
            long likes = images.stream().mapToLong(MapImage::getLikeCount).sum();
            if (likes == 0) continue;
            MapImage representative = images.stream().max(Comparator.comparing(MapImage::getLikeCount).thenComparing(MapImage::getId, Comparator.reverseOrder())).orElse(null);
            rows.add(new Row(place, distance, likes, images.size(), representative));
        }
        rows.sort(Comparator.comparingLong(Row::likes).reversed().thenComparing(r -> r.place().getId()));
        boolean expanded = false;
        double applied = requested;
        if (scope == PlaceRankingScope.LOCAL && rows.size() < safeLimit && requested < MAX_RADIUS) {
            applied = MAX_RADIUS; expanded = true;
            rows.clear();
            for (MapPlace place : placeRepository.findAll()) {
                if (category != null && !category.isBlank() && !category.equalsIgnoreCase(place.getCategory())) continue;
                double distance = distance(latitude, longitude, place.getLatitude(), place.getLongitude());
                if (distance > applied) continue;
                List<MapImage> images = imageRepository.findByMapPlace_Id(place.getId()).stream().filter(i -> i.getVisibilityStatus() == MapImageVisibilityStatus.ACTIVE).toList();
                long likes = images.stream().filter(i -> i.getCreatedAt() != null && !i.getCreatedAt().toInstant(ZoneOffset.UTC).isBefore(start)).mapToLong(MapImage::getLikeCount).sum();
                if (likes > 0) rows.add(new Row(place, distance, likes, images.size(), images.stream().max(Comparator.comparing(MapImage::getLikeCount)).orElse(null)));
            }
            rows.sort(Comparator.comparingLong(Row::likes).reversed().thenComparing(r -> r.place().getId()));
        }
        long total = rows.size(); int totalPages = (int) Math.ceil(total / (double) safeLimit);
        int from = Math.min((safePage - 1) * safeLimit, rows.size()), to = Math.min(from + safeLimit, rows.size());
        Set<Long> bookmarked = userId == null ? Set.of() : bookmarkRepository.findPlaceIdsByUserIdAndPlaceIds(userId, rows.subList(from, to).stream().map(r -> r.place().getId()).toList());
        List<PlaceRankingResponse.Item> items = new ArrayList<>();
        for (int i = from; i < to; i++) {
            Row r = rows.get(i); MapImage image = r.image(); String source = image == null ? "NONE" : "POST";
            items.add(new PlaceRankingResponse.Item(i + 1, r.place().getId(), r.place().getName(), r.place().getCategory(), r.place().getLatitude(), r.place().getLongitude(), scope == PlaceRankingScope.LOCAL ? Math.round(r.distance() * 1000) : null, (double) r.likes(), r.likes(), r.postCount(), image == null ? r.place().getImageUrl() : image.getImageUrl(), image == null ? null : image.getThumbnailUrl(), source, image == null ? null : image.getId(), null, r.place().getRegistrant(), userId == null ? null : bookmarked.contains(r.place().getId())));
        }
        return new PlaceRankingResponse(scope, safePeriod, start, end, "POST_LIKE_COUNT", end, scope == PlaceRankingScope.LOCAL ? requested : null, scope == PlaceRankingScope.LOCAL ? applied : null, expanded, items, safePage, safeLimit, total, totalPages, safePage < totalPages);
    }
    private double distance(Double lat1, Double lon1, Double lat2, Double lon2) { if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return Double.MAX_VALUE; double p = Math.PI / 180; double a = .5 - Math.cos((lat2-lat1)*p)/2 + Math.cos(lat1*p)*Math.cos(lat2*p)*(1-Math.cos((lon2-lon1)*p))/2; return 12742 * Math.asin(Math.sqrt(a)); }
    private record Row(MapPlace place, double distance, long likes, long postCount, MapImage image) {}
}
