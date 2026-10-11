package com.typenull.pingdom.community.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자·게시글·KST 날짜별 최초 조회 기록 결과")
public record CommunityPostViewResponse(Long postId, long viewCount) { }
