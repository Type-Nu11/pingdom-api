package com.typenull.pingdom.place.application.service.recommendation.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.exception.MapException;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceRecommendationSnapshotWriteLockTest {
    @Mock private MapPlaceRepository places;
    @Mock private EntityManager entityManager;
    @Mock private Supplier<List<PlaceRecommendationSnapshot>> query;

    @Test
    void locksDistinctIdsInCanonicalOrder() {
        var lock = new PlaceRecommendationSnapshotWriteLock(places, entityManager);
        lock.lockPlaces(List.of(8L, 2L, 8L, 3L));
        verify(places).findAllByIdInForUpdate(List.of(2L, 3L, 8L));
    }

    @Test
    void flushesLocalChangesBeforeRefreshingTheLockedManagedPlace() {
        var place = MapPlace.builder().id(7L).photoCount(1L).build();
        when(places.findByIdForUpdate(7L)).thenReturn(Optional.of(place));
        org.mockito.Mockito.doAnswer(invocation -> {
            place.replacePhotoCount(5L);
            return null;
        }).when(entityManager).refresh(place);
        var lock = new PlaceRecommendationSnapshotWriteLock(places, entityManager);

        assertThat(lock.requirePlace(7L)).isSameAs(place);
        assertThat(place.currentPhotoCount()).isEqualTo(5L);

        var order = inOrder(places, entityManager);
        order.verify(places).findByIdForUpdate(7L);
        order.verify(entityManager).flush();
        order.verify(entityManager).refresh(place);
        org.mockito.Mockito.verify(entityManager, org.mockito.Mockito.never()).clear();
    }

    @Test
    void refreshesOnlyLockedPlacesAfterAcquiringEveryOrderedLock() {
        var first = MapPlace.builder().id(2L).build();
        var second = MapPlace.builder().id(8L).build();
        when(places.findAllByIdInForUpdate(List.of(2L, 8L))).thenReturn(List.of(first, second));
        var lock = new PlaceRecommendationSnapshotWriteLock(places, entityManager);

        assertThat(lock.lockPlaces(List.of(8L, 2L, 8L))).containsExactly(first, second);

        var order = inOrder(places, entityManager);
        order.verify(places).findAllByIdInForUpdate(List.of(2L, 8L));
        order.verify(entityManager).flush();
        order.verify(entityManager).refresh(first);
        order.verify(entityManager).refresh(second);
        org.mockito.Mockito.verify(entityManager, org.mockito.Mockito.never()).clear();
    }

    @Test
    void rejectsMissingPlaceBeforeWritingSourceOrSnapshots() {
        when(places.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        var lock = new PlaceRecommendationSnapshotWriteLock(places, entityManager);
        assertThatThrownBy(() -> lock.requirePlaces(List.of(7L))).isInstanceOf(MapException.class);
    }

    @Test
    void flushesPriorIncrementsAndDetachesOnlyLoadedSnapshotsBeforeFreshQuery() {
        var stale = PlaceRecommendationSnapshot.builder().placeId(7L).clickCount(1L).build();
        var fresh = PlaceRecommendationSnapshot.builder().placeId(7L).clickCount(2L).build();
        when(query.get()).thenReturn(List.of(stale), List.of(fresh));
        var lock = new PlaceRecommendationSnapshotWriteLock(places, entityManager);

        assertThat(lock.reloadSnapshots(query)).containsExactly(fresh);

        var order = inOrder(entityManager, query);
        order.verify(entityManager).flush();
        order.verify(query).get();
        order.verify(entityManager).detach(stale);
        order.verify(query).get();
        org.mockito.Mockito.verify(entityManager, org.mockito.Mockito.never()).clear();
    }
}
