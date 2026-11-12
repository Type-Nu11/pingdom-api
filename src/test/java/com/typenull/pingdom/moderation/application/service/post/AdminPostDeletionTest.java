package com.typenull.pingdom.moderation.application.service.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.application.service.MapImageStateLockService;
import com.typenull.pingdom.post.application.service.MapImageStateLockService.LockedImage;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminPostDeletionTest {

    private static final Long POST_ID = 3L;
    private static final Long ADMIN_ID = 99L;

    @Mock private MapImageRepository images;
    @Mock private PostReportRepository reports;
    @Mock private MapPlaceRepository places;
    @Mock private MapImageStateLockService imageLocks;
    @Mock private S3ObjectDeleteOutboxPublisher s3;
    @Mock private AdminAuditLogService audit;
    @Mock private AdminRoleAuthorizationService authorization;

    private PlaceGrowthService growth;
    private AdminPostServiceImpl service;

    @BeforeEach
    void setUp() {
        growth = spy(new PlaceGrowthService(places));
        service = new AdminPostServiceImpl(images, reports, growth, imageLocks,
                s3, audit, authorization, Clock.systemUTC());
    }

    @Test
    void activeDeletionCountsOnceOnFreshLockedPlaceAndPreservesOutboxAudit() {
        MapPlace place = MapPlace.builder().id(10L).photoCount(2L).build();
        MapImage image = image(place, MapImageVisibilityStatus.ACTIVE);
        stubDeletion(image, place);

        service.deletePost(POST_ID, ADMIN_ID);

        assertThat(place.currentPhotoCount()).isEqualTo(1L);
        verify(growth).decreasePhotoCount(place);
        verify(growth, never()).decreasePhotoCount(10L);
        verifyNoInteractions(places);
        verify(s3).publish("original-key", "MAP_IMAGE", "3", "ADMIN_MAP_IMAGE_DELETED");
        verify(s3).publish("thumbnail-key", "MAP_IMAGE", "3", "ADMIN_MAP_IMAGE_THUMBNAIL_DELETED");
        ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(audit).record(eq(ADMIN_ID), eq(AdminAuditAction.POST_DELETED),
                eq(AdminAuditTargetType.POST), eq(POST_ID), eq("ADMIN_MAP_IMAGE_DELETED"),
                before.capture(), after.capture());
        assertThat(((Map<?, ?>) before.getValue()).get("deleted")).isEqualTo(false);
        assertThat(((Map<?, ?>) after.getValue()).get("deleted")).isEqualTo(true);

        InOrder order = inOrder(authorization, images, reports, imageLocks, growth, s3, audit);
        order.verify(authorization).requirePermission(ADMIN_ID, AdminPermission.REPORT_REVIEW);
        order.verify(images).findPlaceReferenceById(POST_ID);
        order.verify(reports).findAllByMapImageIdForUpdate(POST_ID);
        order.verify(reports).detachMapImageByMapImageId(POST_ID);
        order.verify(imageLocks).findForUpdate(POST_ID);
        order.verify(growth).decreasePhotoCount(place);
        order.verify(images).delete(image);
    }

    @Test
    void hideThenDeleteKeepsRemainingActivePhotoCount() {
        MapPlace place = MapPlace.builder().id(10L).photoCount(2L).build();
        MapImage image = image(place, MapImageVisibilityStatus.ACTIVE);
        stubDeletion(image, place);

        service.hidePost(POST_ID, "hide", ADMIN_ID);
        service.deletePost(POST_ID, ADMIN_ID);

        assertThat(place.currentPhotoCount()).isEqualTo(1L);
        verify(growth).decreasePhotoCount(place);
        verify(images).delete(image);
    }

    @Test
    void hiddenDeletionUsesStateReloadedAfterBulkClearWithoutAnotherDecrement() {
        MapPlace place = MapPlace.builder().id(10L).photoCount(1L).build();
        MapImage image = image(place, MapImageVisibilityStatus.AUTO_HIDDEN);
        stubDeletion(image, place);

        service.deletePost(POST_ID, ADMIN_ID);

        assertThat(place.currentPhotoCount()).isEqualTo(1L);
        verifyNoInteractions(growth);
        verify(images, never()).findWithMapPlaceById(POST_ID);
        verify(images).delete(image);
        InOrder order = inOrder(reports, imageLocks);
        order.verify(reports).detachMapImageByMapImageId(POST_ID);
        order.verify(imageLocks).findForUpdate(POST_ID);
    }

    @Test
    void restoreThenDeleteCountsOnlyActiveDeletion() {
        MapPlace place = MapPlace.builder().id(10L).photoCount(1L).build();
        MapImage image = image(place, MapImageVisibilityStatus.AUTO_HIDDEN);
        stubDeletion(image, place);

        service.restorePost(POST_ID, "restore", ADMIN_ID);
        service.deletePost(POST_ID, ADMIN_ID);

        assertThat(place.currentPhotoCount()).isEqualTo(1L);
        verify(growth).increasePhotoCount(place);
        verify(growth).decreasePhotoCount(place);
    }

    @Test
    void deletionWithoutPlaceDoesNotChangeGrowth() {
        MapImage image = image(null, MapImageVisibilityStatus.ACTIVE);
        stubDeletion(image, null);

        service.deletePost(POST_ID, ADMIN_ID);

        verifyNoInteractions(growth);
        verify(images).delete(image);
    }

    @Test
    void missingMetadataStopsBeforeReportDetach() {
        assertThatThrownBy(() -> service.deletePost(POST_ID, ADMIN_ID))
                .isInstanceOfSatisfying(AdminException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AdminErrorCode.POST_NOT_FOUND));

        verifyNoInteractions(reports, imageLocks, growth, s3, audit);
    }

    @Test
    void concurrentDeletionAfterMetadataDoesNotPublishSideEffects() {
        var reference = reference(10L);
        when(images.findPlaceReferenceById(POST_ID)).thenReturn(Optional.of(reference));

        assertThatThrownBy(() -> service.deletePost(POST_ID, ADMIN_ID))
                .isInstanceOfSatisfying(AdminException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AdminErrorCode.POST_NOT_FOUND));

        verify(reports).detachMapImageByMapImageId(POST_ID);
        verify(images, never()).delete(any(MapImage.class));
        verifyNoInteractions(growth, s3, audit);
    }

    @Test
    void relocationConflictPropagatesWithoutDeletingOrPublishing() {
        var reference = reference(10L);
        when(images.findPlaceReferenceById(POST_ID)).thenReturn(Optional.of(reference));
        when(imageLocks.findForUpdate(POST_ID)).thenThrow(new MapException(MapErrorCode.IMAGE_PLACE_CHANGED));

        assertThatThrownBy(() -> service.deletePost(POST_ID, ADMIN_ID))
                .isInstanceOfSatisfying(MapException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MapErrorCode.IMAGE_PLACE_CHANGED));

        verify(images, never()).delete(any(MapImage.class));
        verifyNoInteractions(growth, s3, audit);
    }

    @Test
    void immediateDeleteFailurePropagatesBeforeOutboxAndAudit() {
        MapImage image = image(null, MapImageVisibilityStatus.ACTIVE);
        stubDeletion(image, null);
        doThrow(new IllegalStateException("delete failed")).when(images).delete(image);

        assertThatThrownBy(() -> service.deletePost(POST_ID, ADMIN_ID))
                .isInstanceOf(IllegalStateException.class).hasMessage("delete failed");

        verifyNoInteractions(s3, audit);
    }

    private void stubDeletion(MapImage image, MapPlace place) {
        var reference = reference(place == null ? null : place.getId());
        when(images.findPlaceReferenceById(POST_ID)).thenReturn(Optional.of(reference));
        when(imageLocks.findForUpdate(POST_ID)).thenReturn(Optional.of(new LockedImage(image, place)));
    }

    private MapImage image(MapPlace place, MapImageVisibilityStatus status) {
        return MapImage.builder().id(POST_ID).mapPlace(place).visibilityStatus(status)
                .s3Key("original-key").thumbnailS3Key("thumbnail-key").title("photo").build();
    }

    private MapImageRepository.ImagePlaceReference reference(Long placeId) {
        return new MapImageRepository.ImagePlaceReference() {
            public Long getImageId() { return POST_ID; }
            public Long getPlaceId() { return placeId; }
        };
    }
}
