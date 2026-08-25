package com.typenull.pingdom.place.api.dto.place.media;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "장소 탐색용 미디어 생성 요청")
public record PlaceMediaCreateRequest(
        @Size(max = 500, message = "imageUrl은 500자 이하여야 합니다.")
        @Schema(nullable = true, description = "호환성을 위해 수신하지만 서버는 검증된 s3Key로 URL을 생성합니다.")
        String imageUrl,

        @NotBlank(message = "s3Key는 필수입니다.")
        @Size(max = 500, message = "s3Key는 500자 이하여야 합니다.")
        String s3Key,

        @Size(max = 500, message = "thumbnailUrl은 500자 이하여야 합니다.")
        @Schema(nullable = true)
        String thumbnailUrl,

        @Size(max = 500, message = "thumbnailS3Key는 500자 이하여야 합니다.")
        @Schema(nullable = true)
        String thumbnailS3Key,

        @PositiveOrZero(message = "displayOrder는 0 이상이어야 합니다.")
        @Schema(nullable = true)
        Integer displayOrder
) {
}
