package com.typenull.pingdom.place.application.service.recommendation.snapshot;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 추천 원천 기록과 snapshot writer가 공유하는 장소 잠금·잠금 후 재조회 계약. */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class PlaceRecommendationSnapshotWriteLock {

    private final MapPlaceRepository mapPlaceRepository;
    private final EntityManager entityManager;

    public Optional<MapPlace> lockPlace(Long placeId) {
        Optional<MapPlace> place = mapPlaceRepository.findByIdForUpdate(placeId);
        place.ifPresent(lockedPlace -> {
            entityManager.flush();
            entityManager.refresh(lockedPlace);
        });
        return place;
    }

    public List<MapPlace> lockPlaces(Collection<Long> placeIds) {
        List<Long> orderedIds = placeIds.stream().distinct().sorted().toList();
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        if (orderedIds.size() == 1) {
            return lockPlace(orderedIds.getFirst()).stream().toList();
        }
        List<MapPlace> places = mapPlaceRepository.findAllByIdInForUpdate(orderedIds);
        if (!places.isEmpty()) {
            // 잠금 조회도 기존 managed 값을 반환할 수 있으므로 local 변경을 보존한 뒤 해당 장소만 최신화한다.
            entityManager.flush();
            places.forEach(entityManager::refresh);
        }
        return places;
    }

    public List<MapPlace> requirePlaces(Collection<Long> placeIds) {
        List<MapPlace> places = lockPlaces(placeIds);
        if (places.size() != placeIds.stream().distinct().count()) {
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }
        return places;
    }

    public MapPlace requirePlace(Long placeId) {
        return lockPlace(placeId).orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
    }

    // 같은 트랜잭션의 앞선 증가분을 먼저 반영한다. 전체 clear는 다른 도메인의 managed 상태를 깨므로 사용하지 않는다.
    <T> List<T> reloadSnapshots(Supplier<List<T>> query) {
        entityManager.flush();
        List<T> snapshots = query.get();
        snapshots.forEach(entityManager::detach);
        return snapshots.isEmpty() ? snapshots : query.get();
    }

    <T> Optional<T> reloadSnapshot(Supplier<Optional<T>> query) {
        entityManager.flush();
        Optional<T> snapshot = query.get();
        snapshot.ifPresent(entityManager::detach);
        return snapshot.isEmpty() ? snapshot : query.get();
    }

    void flushAndDetach(Collection<?> snapshots) {
        entityManager.flush();
        snapshots.forEach(entityManager::detach);
    }
}
