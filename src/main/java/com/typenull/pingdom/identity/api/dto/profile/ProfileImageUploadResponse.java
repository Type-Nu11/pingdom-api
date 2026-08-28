package com.typenull.pingdom.identity.api.dto.profile;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로필 이미지 변경 응답")
public record ProfileImageUploadResponse(
        @Schema(description = "변경된 프로필 이미지 URL", example = "https://cdn.pingdom.com/users/profile-images/1/profile.jpg")
        String profileImageUrl
) {
}
