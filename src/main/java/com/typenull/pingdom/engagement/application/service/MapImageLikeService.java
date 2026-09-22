package com.typenull.pingdom.engagement.application.service;

import com.typenull.pingdom.engagement.domain.MapImageLike;
import com.typenull.pingdom.notification.domain.Notifications;
import com.typenull.pingdom.notification.domain.exception.NotificationsErrorCode;
import com.typenull.pingdom.notification.domain.exception.NotificationsException;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationsRepository;
import com.typenull.pingdom.notification.outbox.MapImageLikedOutboxPayload;
import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationConversionService;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversionType;
import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.post.application.query.PostQueryService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 좋아요 이력과 집계, 장소 추천 스냅샷 및 알림 Outbox 발행을 연결합니다.
 * 중복 좋아요는 특정 유일 제약 오류로 변환하며, 좋아요 취소는 이미 없는 요청을 성공으로 간주하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class MapImageLikeService {

    private static final String MAP_IMAGE_LIKE_UNIQUE_CONSTRAINT = "uk_map_image_like_user_image";

    private final MapImageLikeRepository mapImageLikeRepository;
    private final MapImageRepository mapImageRepository;
    private final PostQueryService postQueryService;
    private final OutboxEventPublisher outboxEventPublisher;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final PlaceRecommendationConversionService placeRecommendationConversionService;
    private final NotificationsRepository notificationsRepository;

    /**
     * 좋아요를 저장·flush한 뒤 집계를 증가시키고 연결 장소가 있으면 추천 스냅샷과 전환을 갱신합니다.
     * 푸시를 즉시 전송하지 않고 저장된 좋아요 ID를 중복 방지 키로 사용하는 Outbox 이벤트를 남깁니다.
     */
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

        MapImageLike savedLike = saveLike(mapImageLike);
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

    /**
     * 기존 좋아요와 게시글을 확인한 뒤 반응 행을 삭제하고 좋아요 수와 연결 장소의 추천 snapshot을 갱신한다.
     * 이미 취소된 요청은 NOT_LIKED로 거절하며 기존 추천 전환 이력이나 알림을 되돌리지는 않는다.
     */
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
        mapImageLikeRepository.deleteByUserIdAndMapImageId(userId, mapImageId);
        mapImageRepository.decreaseLikeCount(mapImageId);
        if (mapImage.getMapPlace() != null) {
            placeRecommendationSnapshotService.refresh(mapImage.getMapPlace().getId());
        }

        return new MapImageLikeResult(userId, mapImageId, "좋아요 취소되었습니다.");
    }

    /**
     * 게시글 상세 접근 가능 여부와 요청자 소유 알림의 존재를 확인한 뒤 읽음 처리한다.
     * 전달한 게시글과 알림의 대상이 서로 일치하는지는 이 메서드에서 검사하지 않는다.
     */
    @Transactional
    public void likeReturn(Long postId, Long notificationsId, Long userId) {
        postQueryService.getPost(postId, userId);

        Notifications notification = notificationsRepository
                .findByIdAndUserId(notificationsId, userId)
                .orElseThrow(() -> new NotificationsException(NotificationsErrorCode.NOTIFICATION_NOT_FOUND));

        notification.markAsRead();

    }

    private MapImageLike saveLike(MapImageLike mapImageLike) {
        try {
            // 경쟁 요청의 고유 제약 위반을 이 지점에서 표준 예외로 변환한다.
            return mapImageLikeRepository.saveAndFlush(mapImageLike);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, MAP_IMAGE_LIKE_UNIQUE_CONSTRAINT)) {
                throw new MapException(MapErrorCode.ALREADY_LIKED);
            }
            throw exception;
        }
    }

    private boolean hasConstraint(Throwable throwable, String constraintName) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
