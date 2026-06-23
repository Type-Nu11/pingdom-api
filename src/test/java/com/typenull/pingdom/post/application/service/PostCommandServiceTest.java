package com.typenull.pingdom.post.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.application.service.place.MapPlaceService;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.api.dto.image.PostResponse;
import com.typenull.pingdom.post.api.dto.image.PostUpdateRequest;
import com.typenull.pingdom.post.application.port.PostImageStorage;
import com.typenull.pingdom.post.application.port.PostImageStorage.PostImageStorageError;
import com.typenull.pingdom.post.application.port.PostImageStorage.PostImageStorageException;
import com.typenull.pingdom.post.application.port.PostImageStorage.PostImageUploadResult;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.storage.s3.outbox.S3ObjectDeleteOutboxPublisher;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@ExtendWith(MockitoExtension.class)
class PostCommandServiceTest {

    @Mock
    private PostImageStorage postImageStorage;

    @Mock
    private MapImageRepository mapImageRepository;

    @Mock
    private MapPlaceRepository mapPlaceRepository;

    @Mock
    private PostReportRepository postReportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MapPlaceService mapPlaceService;

    @Mock
    private PlaceGrowthService placeGrowthService;

    @Mock
    private PlaceRecommendationSnapshotService placeRecommendationSnapshotService;

    @Mock
    private S3ObjectDeleteOutboxPublisher s3ObjectDeleteOutboxPublisher;

    private PostCommandService postCommandService;

    @BeforeEach
    void setUp() {
        postCommandService = new PostCommandService(
                postImageStorage,
                mapImageRepository,
                mapPlaceRepository,
                postReportRepository,
                userRepository,
                mapPlaceService,
                transactionManager(),
                placeGrowthService,
                placeRecommendationSnapshotService,
                s3ObjectDeleteOutboxPublisher
        );
    }

    @Test
    void deletePostDeletesDatabaseRecordBeforePublishingS3DeleteEvent() {
        MapImage mapImage = mapImage();
        when(mapImageRepository.findWithMapPlaceById(10L)).thenReturn(Optional.of(mapImage));

        PostResponse response = postCommandService.deletePost(10L, 1L);

        assertEquals(10L, response.postId());
        InOrder inOrder = inOrder(mapImageRepository, s3ObjectDeleteOutboxPublisher);
        inOrder.verify(mapImageRepository).delete(mapImage);
        inOrder.verify(s3ObjectDeleteOutboxPublisher)
                .publish("map/delete-target.jpg", "MAP_IMAGE", "10", "MAP_IMAGE_DELETED");
        verify(postImageStorage, never()).delete(any());
    }

    @Test
    void updatePostPublishesOldS3DeleteEventInsteadOfDeletingImmediately() {
        MapImage mapImage = mapImage();
        when(mapImageRepository.findWithMapPlaceById(10L)).thenReturn(Optional.of(mapImage));
        when(postImageStorage.upload(any()))
                .thenReturn(new PostImageUploadResult(
                        "map/new-target.jpg",
                        "https://example.com/new-target.jpg"
                ));
        PostUpdateRequest request = new PostUpdateRequest(
                "수정 제목",
                "수정 설명",
                new MockMultipartFile("file", "new.jpg", "image/jpeg", "new-image".getBytes())
        );

        postCommandService.updatePost(request, 1L, 10L);

        assertEquals("map/new-target.jpg", mapImage.getS3Key());
        verify(mapImageRepository).save(mapImage);
        verify(s3ObjectDeleteOutboxPublisher)
                .publish("map/delete-target.jpg", "MAP_IMAGE", "10", "MAP_IMAGE_REPLACED");
        verify(postImageStorage, never()).delete("map/delete-target.jpg");
    }

    @Test
    void updatePostDeletesNewS3ObjectWhenTransactionRollsBack() {
        MapImage mapImage = mapImage();
        when(mapImageRepository.findWithMapPlaceById(10L)).thenReturn(Optional.of(mapImage));
        when(postImageStorage.upload(any()))
                .thenReturn(new PostImageUploadResult(
                        "map/new-target.jpg",
                        "https://example.com/new-target.jpg"
                ));
        doThrow(new RuntimeException("db failure")).when(mapImageRepository).save(mapImage);
        PostUpdateRequest request = new PostUpdateRequest(
                "수정 제목",
                "수정 설명",
                new MockMultipartFile("file", "new.jpg", "image/jpeg", "new-image".getBytes())
        );

        assertThrows(RuntimeException.class, () -> postCommandService.updatePost(request, 1L, 10L));

        verify(postImageStorage).delete("map/new-target.jpg");
        verify(s3ObjectDeleteOutboxPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void updatePostMapsStorageFailureToUploadError() {
        MapImage mapImage = mapImage();
        when(mapImageRepository.findWithMapPlaceById(10L)).thenReturn(Optional.of(mapImage));
        when(postImageStorage.upload(any()))
                .thenThrow(new PostImageStorageException(PostImageStorageError.STORAGE_ERROR, "storage failure", null));
        PostUpdateRequest request = new PostUpdateRequest(
                "수정 제목",
                "수정 설명",
                new MockMultipartFile("file", "new.jpg", "image/jpeg", "new-image".getBytes())
        );

        MapException exception = assertThrows(
                MapException.class,
                () -> postCommandService.updatePost(request, 1L, 10L)
        );

        assertEquals(MapErrorCode.UPLOAD_ERROR, exception.getErrorCode());
        verify(mapImageRepository, never()).save(any());
        verify(s3ObjectDeleteOutboxPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void deletePostDoesNotDeleteOrPublishS3WhenDatabaseDeleteFails() {
        MapImage mapImage = mapImage();
        when(mapImageRepository.findWithMapPlaceById(10L)).thenReturn(Optional.of(mapImage));
        doThrow(new RuntimeException("db failure"))
                .when(mapImageRepository)
                .delete(mapImage);

        assertThrows(RuntimeException.class, () -> postCommandService.deletePost(10L, 1L));

        verify(postImageStorage, never()).delete(any());
        verify(s3ObjectDeleteOutboxPublisher, never()).publish(any(), any(), any(), any());
    }

    private MapImage mapImage() {
        return MapImage.builder()
                .id(10L)
                .imageUrl("https://example.com/delete-target.jpg")
                .s3Key("map/delete-target.jpg")
                .title("삭제 대상")
                .description("삭제 대상 설명")
                .userId(1L)
                .username("writer")
                .build();
    }

    private PlatformTransactionManager transactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
    }
}
