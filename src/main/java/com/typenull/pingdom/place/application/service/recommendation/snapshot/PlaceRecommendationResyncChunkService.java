package com.typenull.pingdom.place.application.service.recommendation.snapshot;

import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService;
import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService.TrendProfile;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncCheckpointRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncCheckpointRepository.Checkpoint;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncCheckpointRepository.Counts;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncCheckpointRepository.Phase;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncQueryRepository.ExistingPair;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncQueryRepository.Pair;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncQueryRepository.Score;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 실행권 상태 행 → 장소 ID 오름차순 잠금. 실제 변경과 커서는 REQUIRES_NEW 한 청크로 커밋한다. */
@Service
@RequiredArgsConstructor
public class PlaceRecommendationResyncChunkService {
    private final RecommendationResyncCheckpointRepository checkpoints;
    private final RecommendationResyncQueryRepository queries;
    private final PlaceRecommendationSnapshotResyncService snapshots;
    private final PlaceRecommendationVersionSnapshotService versions;
    private final PlaceRecommendationSimilarityService similarity;
    private final PlaceRecommendationSnapshotWriteLock writeLock;

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 60)
    public void claim(UUID token) {
        checkpoints.claim(token, similarity.cachedTotalBookmarkUserCount());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 60)
    public Checkpoint execute(UUID token) {
        Checkpoint state = checkpoints.lock(token);
        Checkpoint next = switch (state.phase()) {
            case COUNTERS -> counters(state);
            case SIMILARITY -> similarity(state);
            case SNAPSHOT_ORPHANS -> snapshotOrphans(state);
            case VERSION_ORPHANS -> versionOrphans(state);
            case SIMILARITY_STALE -> staleSimilarity(state);
            case DONE -> state;
        };
        checkpoints.save(token, next);
        return next;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 60)
    public void release(UUID token) {
        checkpoints.release(token);
    }

    private Checkpoint counters(Checkpoint state) {
        List<Long> ids = queries.placeIds(state.left(), state.upperPlaceId());
        if (ids.isEmpty()) return state.advance(Phase.SIMILARITY, 0, 0, Counts.empty());
        List<MapPlace> places = writeLock.lockPlaces(ids);
        long synchronizedCount = snapshots.synchronizeSnapshots(places, LocalDateTime.now());
        var versionResult = versions.synchronizeVersions(places.stream().map(MapPlace::getId).toList(), LocalDateTime.now());
        return state.advance(Phase.COUNTERS, ids.getLast(), 0, new Counts(0, synchronizedCount, 0, 0, 0,
                versionResult.synchronizedSnapshotCount(), versionResult.deletedSnapshotCount()));
    }

    private Checkpoint similarity(Checkpoint state) {
        List<Pair> pairs = queries.pairs(state.left(), state.right(), state.upperPlaceId());
        if (pairs.isEmpty()) return state.advance(Phase.SNAPSHOT_ORPHANS, 0, 0, Counts.empty());
        List<Long> ids = pairs.stream().flatMap(pair -> Stream.of(pair.left(), pair.right())).distinct().sorted().toList();
        Map<Long, MapPlace> places = writeLock.lockPlaces(ids).stream()
                .collect(Collectors.toMap(MapPlace::getId, Function.identity()));
        List<Score> scores = queries.signals(pairs).stream().map(signal -> {
            var score = similarity.scoreFromCounts(places.get(signal.left()), places.get(signal.right()),
                    signal.bookmarkLeft(), signal.bookmarkRight(), signal.bookmarkIntersection(),
                    signal.likeLeft(), signal.likeRight(), signal.likeIntersection(), state.totalBookmarkUsers(),
                    TrendProfile.of(signal.photoLeft(), signal.latestLeft()),
                    TrendProfile.of(signal.photoRight(), signal.latestRight()));
            return new Score(signal.left(), signal.right(), score.geoKernel(), score.coBookmarkPmi(),
                    score.coLikeCosine(), score.trendSimilarity(), score.totalSimilarity(), LocalDateTime.now());
        }).toList();
        queries.upsert(scores);
        Pair last = pairs.getLast();
        return state.advance(Phase.SIMILARITY, last.left(), last.right(),
                new Counts(0, 0, 0, scores.size(), 0, 0, 0));
    }

    private Checkpoint snapshotOrphans(Checkpoint state) {
        List<Long> ids = queries.snapshotOrphans(state.left(), state.upperSnapshotId());
        if (ids.isEmpty()) return state.advance(Phase.VERSION_ORPHANS, 0, 0, Counts.empty());
        int deleted = queries.deleteSnapshotOrphans(ids);
        return state.advance(Phase.SNAPSHOT_ORPHANS, ids.getLast(), 0, new Counts(0, 0, deleted, 0, 0, 0, 0));
    }

    private Checkpoint versionOrphans(Checkpoint state) {
        List<Long> ids = queries.versionOrphans(state.left(), state.upperVersionId());
        if (ids.isEmpty()) return state.advance(Phase.SIMILARITY_STALE, 0, 0, Counts.empty());
        int deleted = queries.deleteVersionOrphans(ids);
        return state.advance(Phase.VERSION_ORPHANS, ids.getLast(), 0, new Counts(0, 0, 0, 0, 0, 0, deleted));
    }

    private Checkpoint staleSimilarity(Checkpoint state) {
        List<ExistingPair> pairs = queries.existingPairs(state.left(), state.upperSimilarityId());
        if (pairs.isEmpty()) return state.advance(Phase.DONE, 0, 0, Counts.empty());
        writeLock.lockPlaces(pairs.stream().flatMap(pair -> Stream.of(pair.left(), pair.right()))
                .distinct().sorted().toList());
        int deleted = queries.deleteInvalidPairs(pairs.stream().map(ExistingPair::id).toList());
        return state.advance(Phase.SIMILARITY_STALE, pairs.getLast().id(), 0, new Counts(0, 0, 0, 0, deleted, 0, 0));
    }
}
