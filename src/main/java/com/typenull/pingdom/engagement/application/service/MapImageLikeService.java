package com.typenull.pingdom.engagement.application.service;

import com.typenull.pingdom.engagement.domain.MapImageLike;
import com.typenull.pingdom.engagement.event.MapImageLikedEvent;
import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
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
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public MapImageLikeResult like(Long mapImageId, Long userId) {
        if (mapImageLikeRepository.existsByUserIdAndMapImageId(
                userId,
                mapImageId
        )) {
            throw new MapException(MapErrorCode.ALREADY_LIKED);
        }

        Long ownerId = mapImageRepository.findById(
                mapImageId
        ).map(mapImage -> mapImage.getUserId())
                .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));

        MapImageLike mapImageLike = MapImageLike.builder()
                .mapImageId(mapImageId)
                .userId(userId)
                .build();

        mapImageLikeRepository.save(mapImageLike);
        mapImageRepository.increaseLikeCount(mapImageId);
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

        mapImageLikeRepository.deleteLike(userId, mapImageId);
        mapImageRepository.decreaseLikeCount(mapImageId);

        return new MapImageLikeResult(userId, mapImageId, "좋아요 취소되었습니다.");
    }
}
