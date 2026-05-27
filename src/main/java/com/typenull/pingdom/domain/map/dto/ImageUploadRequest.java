package com.typenull.pingdom.domain.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "게시글 업로드 요청 정보")
public record ImageUploadRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Schema(description = "게시글 제목", example = "진주성 야경")
        String title,

        @Schema(description = "게시글 부가 설명", example = "비 온 뒤라 반사가 예쁘게 나왔습니다.")
        String description,

        @NotNull(message = "파일은 필수입니다.")
        @Schema(description = "업로드할 이미지 파일", type = "string", format = "binary")
        MultipartFile file) {
}
