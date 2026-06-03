package com.typenull.pingdom.engagement.event;

// 지도 게시글 좋아요 확정 이후 후속 처리를 위한 이벤트
public record MapImageLikedEvent(
        Long mapImageId,
        Long ownerId,
        Long likerId
) {
}
