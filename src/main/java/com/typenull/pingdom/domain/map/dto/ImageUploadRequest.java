package com.typenull.pingdom.domain.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "게시글 업로드 요청 정보")
public record ImageUploadRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
        @Schema(description = "게시글 제목", example = "진주성 야경")
        String title,

        @Size(max = 1000, message = "부가 설명은 1000자 이하여야 합니다.")
        @Schema(description = "게시글 부가 설명", example = "비 온 뒤라 반사가 예쁘게 나왔습니다.")
        String description,

        @Size(max = 50, message = "카카오 장소 ID는 50자 이하여야 합니다.")
        @Schema(description = "카카오 장소 ID(권장)", example = "27414316")
        String kakaoPlaceId,

        @Schema(description = "연결할 장소 ID(레거시)", example = "17")
        Long placeId,

        @NotNull(message = "파일은 필수입니다.")
        @Schema(description = "업로드할 게시글 첨부 파일", type = "string", format = "binary")
        MultipartFile file
) {
}
