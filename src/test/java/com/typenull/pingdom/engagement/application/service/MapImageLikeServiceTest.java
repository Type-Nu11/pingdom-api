package com.typenull.pingdom.engagement.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.domain.MapImageLike;
import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.notification.outbox.MapImageLikedOutboxPayload;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationsRepository;
import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationConversionService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.post.application.query.PostQueryService;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class MapImageLikeServiceTest {

    @Mock
    private MapImageLikeRepository mapImageLikeRepository;

    @Mock
    private MapImageRepository mapImageRepository;

    @Mock
    private PostQueryService postQueryService;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @Mock
    private PlaceRecommendationSnapshotService placeRecommendationSnapshotService;

    @Mock
    private PlaceRecommendationConversionService placeRecommendationConversionService;

    @Mock
    private NotificationsRepository notificationsRepository;

    @InjectMocks
    private MapImageLikeService mapImageLikeService;

    /**
     * 사진 좋아요 저장 결과의 ID로 MAP_IMAGE_LIKED 이벤트 키를 만들고 사진 집계 ID와 알림 payload를 Outbox에 발행하는지 검증.
     */
    @Test
    void likeStoresFcmOutboxEvent() {
        long mapImageId = 10L;
        long ownerId = 20L;
        long likerId = 30L;
        MapImage mapImage = MapImage.builder()
                .id(mapImageId)
                .userId(ownerId)
                .imageUrl("https://example.com/image.jpg")
                .s3Key("image-key")
                .title("title")
                .build();
        when(mapImageLikeRepository.existsByUserIdAndMapImageId(likerId, mapImageId)).thenReturn(false);
        when(mapImageRepository.findWithMapPlaceById(mapImageId)).thenReturn(Optional.of(mapImage));
        when(mapImageLikeRepository.saveAndFlush(any(MapImageLike.class))).thenReturn(
                MapImageLike.builder()
                        .likeId(40L)
                        .mapImageId(mapImageId)
                        .userId(likerId)
                        .build()
        );

        mapImageLikeService.like(mapImageId, likerId);

        verify(outboxEventPublisher).publish(
                eq("MAP_IMAGE_LIKED:40"),
                eq(OutboxEventType.MAP_IMAGE_LIKED),
                any(MapImageLikedOutboxPayload.class),
                eq("MAP_IMAGE"),
                eq("10")
        );
    }

    /**
     * 사전 조회 후 좋아요 고유 제약이 발생하면 ALREADY_LIKED로 변환하고 카운터 증가·Outbox 발행은 하지 않는지 검증.
     * 실제 동시 요청 실행 대신 제약 위반을 주입해 경합 실패 경로를 재현.
     */
    @Test
    void mapsDuplicateLikeConstraint() {
        long mapImageId = 10L;
        long likerId = 30L;
        MapImage mapImage = MapImage.builder()
                .id(mapImageId)
                .userId(20L)
                .imageUrl("https://example.com/image.jpg")
                .s3Key("image-key")
                .title("title")
                .build();
        ConstraintViolationException constraintViolation = new ConstraintViolationException(
                "duplicate like", new SQLException(), "uk_map_image_like_user_image");

        when(mapImageLikeRepository.existsByUserIdAndMapImageId(likerId, mapImageId)).thenReturn(false);
        when(mapImageRepository.findWithMapPlaceById(mapImageId)).thenReturn(Optional.of(mapImage));
        when(mapImageLikeRepository.saveAndFlush(any(MapImageLike.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate like", constraintViolation));

        assertThatThrownBy(() -> mapImageLikeService.like(mapImageId, likerId))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.ALREADY_LIKED));

        verify(mapImageRepository, never()).increaseLikeCount(mapImageId);
        verify(outboxEventPublisher, never()).publish(any(), any(), any(), any(), any());
    }
}
