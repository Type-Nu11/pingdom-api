package com.typenull.pingdom.domain.admin.dto.picture;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 사진 조회 응답")
public record AdminPictureResponse(
        @Schema(description = "사진 ID", example = "10")
        Long id,
        @Schema(description = "사진 접근 URL", example = "https://cdn.example.com/pictures/10.jpg")
        String imageUrl,
        @Schema(description = "S3 저장 키", example = "pictures/2026/05/10/sample.jpg")
        String s3Key,
        @Schema(description = "사진 소유 사용자 ID", example = "3")
        Long userId
) {
}
