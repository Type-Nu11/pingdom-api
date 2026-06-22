package com.typenull.pingdom.engagement.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.domain.MapImageLike;
import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.notification.outbox.MapImageLikedOutboxPayload;
import com.typenull.pingdom.notification.repository.NotificationsRepository;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationConversionService;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.post.application.query.PostQueryService;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        when(mapImageLikeRepository.save(any(MapImageLike.class))).thenReturn(
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
}
