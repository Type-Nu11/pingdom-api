package com.typenull.pingdom.domain.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "사진 업로드 요청 정보")
public record ImageUploadRequest(
        @NotNull(message = "파일은 필수입니다.")
        @Schema(description = "업로드할 이미지 파일", type = "string", format = "binary")
        MultipartFile file) {
}
