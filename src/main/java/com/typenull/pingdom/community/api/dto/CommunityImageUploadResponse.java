package com.typenull.pingdom.community.api.dto;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "게시글에 연결할 이미지. 본인 미사용 이미지만 만료 전 1회 연결 가능")
public record CommunityImageUploadResponse(Long imageId, String imageUrl, LocalDateTime expiresAt) {}
