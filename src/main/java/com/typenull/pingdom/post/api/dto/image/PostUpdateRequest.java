package com.typenull.pingdom.post.api.dto.image;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

@Schema(description = "게시글 수정 요청 정보")
public record PostUpdateRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
        @Schema(description = "게시글 제목", example = "진주성 야경")
        String title,

        @Size(max = 1000, message = "부가 설명은 1000자 이하여야 합니다.")
        @Schema(description = "게시글 부가 설명", example = "비 온 뒤라 반사가 예쁘게 나왔습니다.")
        String description,

        @NotNull(message = "파일은 필수입니다.")
        @Schema(description = "수정할 게시글 첨부 파일", type = "string", format = "binary")
        MultipartFile file
) {
}
