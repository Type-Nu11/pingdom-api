package com.typenull.pingdom.community.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "댓글 좋아요 수와 현재 사용자 상태")
public record CommunityCommentLikeResponse(Long commentId, long likeCount, boolean liked) { }
