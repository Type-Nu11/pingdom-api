package com.typenull.pingdom.community.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "게시글 수정 결과")
public record CommunityPostUpdateResponse(Long postId, LocalDateTime updatedAt) {}
