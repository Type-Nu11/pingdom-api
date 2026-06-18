package com.typenull.pingdom.engagement.application.service;

import com.typenull.pingdom.engagement.domain.MapImageLike;
import com.typenull.pingdom.engagement.event.MapImageLikedEvent;
import com.typenull.pingdom.notification.domain.Notifications;
import com.typenull.pingdom.notification.domain.exception.NotificationsErrorCode;
import com.typenull.pingdom.notification.domain.exception.NotificationsException;
import com.typenull.pingdom.notification.repository.NotificationsRepository;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationConversionService;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationConversionType;
import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.post.application.query.PostQueryService;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MapImageLikeService {

    private final MapImageLikeRepository mapImageLikeRepository;
    private final MapImageRepository mapImageRepository;
    private final PostQueryService postQueryService;
    private final ApplicationEventPublisher applicationEventPublisher;
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

        mapImageLikeRepository.save(mapImageLike);
        mapImageRepository.increaseLikeCount(mapImageId);
        if (mapImage.getMapPlace() != null) {
            placeRecommendationSnapshotService.refresh(mapImage.getMapPlace().getId());
            placeRecommendationConversionService.recordConversionIfEligible(
                    userId,
                    mapImage.getMapPlace().getId(),
                    PlaceRecommendationConversionType.LIKE
            );
        }
        // 좋아요 확정 이후 부수효과는 커밋 후 이벤트 리스너가 처리한다.
        applicationEventPublisher.publishEvent(new MapImageLikedEvent(mapImageId, ownerId, userId));

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
    public void likeReturn(Long postId, Long notificationsId, Long userId) {
        postQueryService.getPost(postId);

        Notifications notification = notificationsRepository
                .findByIdAndUserId(notificationsId, userId)
                .orElseThrow(() -> new NotificationsException(NotificationsErrorCode.NOTIFICATION_NOT_FOUND));

        notification.setRead(true);

    }
}
