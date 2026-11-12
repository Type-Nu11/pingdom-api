package com.typenull.pingdom.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MapImageStateLockServiceTest {

    @Mock private MapImageRepository images;
    @Mock private MapPlaceRepository places;
    @Mock private EntityManager entityManager;

    private MapImageStateLockService service;

    @BeforeEach
    void setUp() {
        service = new MapImageStateLockService(images, places, entityManager);
    }

    @Test
    void locksPlaceBeforePhotoAndRefreshesStateAfterWaiting() {
        MapPlace place = MapPlace.builder().id(10L).build();
        MapImage image = MapImage.builder().id(3L).mapPlace(place).build();
        when(images.findPlaceReferenceById(3L)).thenReturn(Optional.of(reference(3L, 10L)));
        when(places.findByIdForUpdate(10L)).thenReturn(Optional.of(place));
        when(images.findByIdForUpdate(3L)).thenReturn(Optional.of(image));
        doNothing().when(entityManager).refresh(place);
        doAnswer(invocation -> {
            image.autoHide("other transaction", LocalDateTime.of(2026, 11, 12, 0, 0), null);
            return null;
        }).when(entityManager).refresh(image);

        var result = service.findForUpdate(3L).orElseThrow();

        assertThat(result.image().isVisible()).isFalse();
        assertThat(result.place()).isSameAs(place);
        InOrder order = inOrder(images, places, entityManager);
        order.verify(images).findPlaceReferenceById(3L);
        order.verify(places).findByIdForUpdate(10L);
        order.verify(entityManager).refresh(place);
        order.verify(images).findByIdForUpdate(3L);
        order.verify(entityManager).refresh(image);
    }

    @Test
    void rejectsReassignedPhotoWithoutAcquiringAnotherPlaceAfterPhotoLock() {
        MapPlace original = MapPlace.builder().id(10L).build();
        MapPlace moved = MapPlace.builder().id(20L).build();
        MapImage image = MapImage.builder().id(3L).mapPlace(moved).build();
        when(images.findPlaceReferenceById(3L)).thenReturn(Optional.of(reference(3L, 10L)));
        when(places.findByIdForUpdate(10L)).thenReturn(Optional.of(original));
        when(images.findByIdForUpdate(3L)).thenReturn(Optional.of(image));

        assertThatThrownBy(() -> service.findForUpdate(3L)).isInstanceOfSatisfying(MapException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MapErrorCode.IMAGE_PLACE_CHANGED));

        verify(places, never()).findByIdForUpdate(20L);
        assertThat(image.isVisible()).isTrue();
    }

    @Test
    void supportsPhotoWithoutPlace() {
        MapImage image = MapImage.builder().id(3L).build();
        when(images.findPlaceReferenceById(3L)).thenReturn(Optional.of(reference(3L, null)));
        when(images.findByIdForUpdate(3L)).thenReturn(Optional.of(image));

        var locked = service.findForUpdate(3L).orElseThrow();

        assertThat(locked.place()).isNull();
        verifyNoInteractions(places);
    }

    @Test
    void missingPhotoDoesNotAcquireOtherLocks() {
        assertThat(service.findForUpdate(3L)).isEmpty();

        verify(images, never()).findByIdForUpdate(3L);
        verifyNoInteractions(places, entityManager);
    }

    @Test
    void treatsRemovedSourcePlaceAsRetryableRelocationConflict() {
        when(images.findPlaceReferenceById(3L)).thenReturn(Optional.of(reference(3L, 10L)));

        assertThatThrownBy(() -> service.findForUpdate(3L)).isInstanceOfSatisfying(MapException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MapErrorCode.IMAGE_PLACE_CHANGED));

        verify(images, never()).findByIdForUpdate(3L);
    }

    private MapImageRepository.ImagePlaceReference reference(Long imageId, Long placeId) {
        return new MapImageRepository.ImagePlaceReference() {
            public Long getImageId() { return imageId; }
            public Long getPlaceId() { return placeId; }
        };
    }
}
