package com.typenull.pingdom.engagement.application.service;

import com.typenull.pingdom.engagement.domain.MapImageLike;
import com.typenull.pingdom.notification.domain.Notifications;
import com.typenull.pingdom.notification.domain.exception.NotificationsErrorCode;
import com.typenull.pingdom.notification.domain.exception.NotificationsException;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationsRepository;
import com.typenull.pingdom.notification.outbox.MapImageLikedOutboxPayload;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationConversionService;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationConversionType;
import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.post.application.query.PostQueryService;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MapImageLikeService {

    private final MapImageLikeRepository mapImageLikeRepository;
    private final MapImageRepository mapImageRepository;
    private final PostQueryService postQueryService;
    private final OutboxEventPublisher outboxEventPublisher;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final PlaceRecommendationConversionService placeRecommendationConversionService;
    private final NotificationsRepository notificationsRepository;

    @Transactional
    public MapImageLikeResult like(Long mapImageId, Long userId) {
        if (mapImageLikeRepository.existsByUserIdAndMapImageId(
                userId,
                mapImageId
        )) {
            throw new MapException(MapErrorCode.ALREADY_LIKED);
        }

        MapImage mapImage = mapImageRepository.findWithMapPlaceById(mapImageId)
                .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));
        Long ownerId = mapImage.getUserId();

        MapImageLike mapImageLike = MapImageLike.builder()
                .mapImageId(mapImageId)
                .userId(userId)
                .build();

        MapImageLike savedLike = mapImageLikeRepository.save(mapImageLike);
        mapImageRepository.increaseLikeCount(mapImageId);
        if (mapImage.getMapPlace() != null) {
            placeRecommendationSnapshotService.refresh(mapImage.getMapPlace().getId());
            placeRecommendationConversionService.recordConversionIfEligible(
                    userId,
                    mapImage.getMapPlace().getId(),
                    PlaceRecommendationConversionType.LIKE
            );
        }
        outboxEventPublisher.publish(
                "MAP_IMAGE_LIKED:%d".formatted(savedLike.getLikeId()),
                OutboxEventType.MAP_IMAGE_LIKED,
                new MapImageLikedOutboxPayload(mapImageId, ownerId, userId),
                "MAP_IMAGE",
                String.valueOf(mapImageId)
        );

        return new MapImageLikeResult(userId, mapImageId, "좋아요 추가되었습니다.");
    }

    @Transactional
    public MapImageLikeResult notLike(Long mapImageId, Long userId) {
        if (!mapImageLikeRepository.existsByUserIdAndMapImageId(
                userId,
                mapImageId
        )) {
            throw new MapException(MapErrorCode.NOT_LIKED);
        }

        MapImage mapImage = mapImageRepository.findWithMapPlaceById(mapImageId)
                .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));
        mapImageLikeRepository.deleteLike(userId, mapImageId);
        mapImageRepository.decreaseLikeCount(mapImageId);
        if (mapImage.getMapPlace() != null) {
            placeRecommendationSnapshotService.refresh(mapImage.getMapPlace().getId());
        }

        return new MapImageLikeResult(userId, mapImageId, "좋아요 취소되었습니다.");
    }

    @Transactional
    //좋아요 알림을 눌렀을때 처리
    public void likeReturn(Long postId, Long notificationsId, Long userId) {
        postQueryService.getPost(postId, userId);

        Notifications notification = notificationsRepository
                .findByIdAndUserId(notificationsId, userId)
                .orElseThrow(() -> new NotificationsException(NotificationsErrorCode.NOTIFICATION_NOT_FOUND));

        notification.setRead(true);

    }
}
