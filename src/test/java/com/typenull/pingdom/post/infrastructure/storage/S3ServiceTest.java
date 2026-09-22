package com.typenull.pingdom.post.infrastructure.storage;

import com.typenull.pingdom.post.infrastructure.storage.image.ImageUploadProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.application.service.place.PlaceMediaService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.api.dto.image.PostResponse;
import com.typenull.pingdom.post.api.dto.image.PostUpdateRequest;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import javax.imageio.ImageIO;
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
class S3ServiceTest {

    @Mock
    private S3ObjectStorage s3ObjectStorage;

    @Mock
    private MapImageRepository mapImageRepository;

    @Mock
    private MapPlaceRepository mapPlaceRepository;

    @Mock
    private PostReportRepository postReportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlaceGrowthService placeGrowthService;

    @Mock
    private PlaceMediaService placeMediaService;

    @Mock
    private PlaceRecommendationSnapshotService placeRecommendationSnapshotService;

    @Mock
    private S3ObjectDeleteOutboxPublisher s3ObjectDeleteOutboxPublisher;

    private S3Service s3Service;

    /**
     * 사진·S3·Outbox 의존성 대역과 실제 이미지 처리기를 연결하고 DB 작업 없는 트랜잭션 대역으로 서비스를 구성.
     */
    @BeforeEach
    void setUp() {
        s3Service = new S3Service(
                s3ObjectStorage,
                mapImageRepository,
                mapPlaceRepository,
                postReportRepository,
                userRepository,
                transactionManager(),
                placeGrowthService,
                placeMediaService,
                placeRecommendationSnapshotService,
                s3ObjectDeleteOutboxPublisher,
                new ImageUploadProcessor()
        );
    }

    /**
     * 사진 삭제 응답 ID를 확인하고 DB 삭제 후 원본·썸네일 삭제 Outbox를 순서대로 발행하며 S3 직접 삭제는 하지 않는지 검증.
     */
    @Test
    void deletesRecordBeforePublishingCleanup() {
        MapImage mapImage = mapImage();
        when(mapImageRepository.findWithMapPlaceById(10L)).thenReturn(Optional.of(mapImage));

        PostResponse response = s3Service.deleteImage(10L, 1L);

        assertEquals(10L, response.postId());
        InOrder inOrder = inOrder(mapImageRepository, s3ObjectDeleteOutboxPublisher);
        inOrder.verify(mapImageRepository).delete(mapImage);
        inOrder.verify(s3ObjectDeleteOutboxPublisher)
                .publish("map/delete-target.jpg", "MAP_IMAGE", "10", "MAP_IMAGE_DELETED");
        inOrder.verify(s3ObjectDeleteOutboxPublisher)
                .publish("map/delete-target-thumbnail.jpg", "MAP_IMAGE", "10", "MAP_IMAGE_THUMBNAIL_DELETED");
        verify(s3ObjectStorage, never()).delete(any());
    }

    /**
     * 사진 교체 시 새 원본·썸네일 키를 저장하고 이전 키 삭제 Outbox를 발행하며 이전 원본은 즉시 삭제하지 않는지 검증.
     */
    @Test
    void publishesCleanupForReplacedImages() throws Exception {
        MapImage mapImage = mapImage();
        when(mapImageRepository.findWithMapPlaceById(10L)).thenReturn(Optional.of(mapImage));
        when(s3ObjectStorage.put(any(byte[].class), anyString(), eq("image/jpeg"), eq("map")))
                .thenReturn(new S3ObjectStorage.S3PutResult(
                        "map/new-target.jpg",
                        "https://example.com/new-target.jpg"
                ));
        when(s3ObjectStorage.put(any(byte[].class), anyString(), eq("image/jpeg"), eq("map/thumbnails")))
                .thenReturn(new S3ObjectStorage.S3PutResult(
                        "map/thumbnails/new-target-thumbnail.jpg",
                        "https://example.com/new-target-thumbnail.jpg"
                ));
        PostUpdateRequest request = new PostUpdateRequest(
                "수정 제목",
                "수정 설명",
                new MockMultipartFile("file", "new.jpg", "image/jpeg", validJpegBytes())
        );

        s3Service.updateImage(request, 1L, 10L);

        assertEquals("map/new-target.jpg", mapImage.getS3Key());
        assertEquals("map/thumbnails/new-target-thumbnail.jpg", mapImage.getThumbnailS3Key());
        verify(mapImageRepository).save(mapImage);
        verify(s3ObjectDeleteOutboxPublisher)
                .publish("map/delete-target.jpg", "MAP_IMAGE", "10", "MAP_IMAGE_REPLACED");
        verify(s3ObjectDeleteOutboxPublisher)
                .publish("map/delete-target-thumbnail.jpg", "MAP_IMAGE", "10", "MAP_IMAGE_THUMBNAIL_REPLACED");
        verify(s3ObjectStorage, never()).delete("map/delete-target.jpg");
    }

    /**
     * DB 사진 삭제가 실패하면 예외가 전파되고 S3 삭제와 삭제 Outbox 발행을 모두 실행하지 않는지 검증.
     */
    @Test
    void preservesS3WhenDatabaseDeletionFails() {
        MapImage mapImage = mapImage();
        when(mapImageRepository.findWithMapPlaceById(10L)).thenReturn(Optional.of(mapImage));
        org.mockito.Mockito.doThrow(new RuntimeException("db failure"))
                .when(mapImageRepository)
                .delete(mapImage);

        assertThrows(RuntimeException.class, () -> s3Service.deleteImage(10L, 1L));

        verify(s3ObjectStorage, never()).delete(any());
        verify(s3ObjectDeleteOutboxPublisher, never()).publish(any(), any(), any(), any());
    }

    /**
     * 작성자 1의 사진 10에 원본·썸네일 URL과 키를 채워 삭제/교체 대상을 제공.
     */
    private MapImage mapImage() {
        return MapImage.builder()
                .id(10L)
                .imageUrl("https://example.com/delete-target.jpg")
                .s3Key("map/delete-target.jpg")
                .thumbnailUrl("https://example.com/delete-target-thumbnail.jpg")
                .thumbnailS3Key("map/delete-target-thumbnail.jpg")
                .title("삭제 대상")
                .description("삭제 대상 설명")
                .userId(1L)
                .username("writer")
                .build();
    }

    /**
     * 실제 ImageIO로 2×2 JPEG를 인코딩해 이미지 유효성 검사를 통과하는 업로드 입력을 제공.
     */
    private byte[] validJpegBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", outputStream);
        return outputStream.toByteArray();
    }

    /**
     * 실제 DB 트랜잭션 없이 서비스의 트랜잭션 경로를 실행하는 no-op 대역을 생성.
     * 커밋·롤백의 저장소 원자성은 검증 범위에서 제외.
     */
    private PlatformTransactionManager transactionManager() {
        return new AbstractPlatformTransactionManager() {
            /**
             * 트랜잭션 대역의 호출마다 빈 상태 객체를 제공.
             */
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            /**
             * DB 연결 없이 트랜잭션 경계 호출을 수용하는 빈 시작 훅.
             */
            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            /**
             * 서비스 호출 흐름 검증을 위한 빈 커밋 훅. 실제 저장소 커밋은 생략.
             */
            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            /**
             * 실패 전파 흐름 검증을 위한 빈 롤백 훅. 실제 DB 복구는 생략.
             */
            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
    }
}
