package com.typenull.pingdom.community.api.dto;

import com.typenull.pingdom.community.domain.CommunityPostMedia;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "게시글 작성 전 임시 사진 업로드 결과")
public record CommunityPostMediaUploadResponse(
        @Schema(description = "작성 요청 imageIds에 넣을 업로드 ID") Long imageId,
        String imageUrl,
        String contentType,
        @Schema(description = "재인코딩 후 저장된 파일의 바이트 수") long fileSize,
        @Schema(description = "미연결 사진의 만료 시각. 업로드 후 24시간") LocalDateTime expiresAt
) {
    public static CommunityPostMediaUploadResponse from(CommunityPostMedia media) {
        return new CommunityPostMediaUploadResponse(media.getId(), media.getImageUrl(), media.getContentType(),
                media.getFileSize(), media.getExpiresAt());
    }
}
