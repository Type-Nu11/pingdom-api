package com.typenull.pingdom.post.api.dto.image;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

@Schema(description = "게시글 업로드 요청 정보")
public record PostUploadRequest(
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

        @Size(max = 100, message = "장소 이름은 100자 이하여야 합니다.")
        @Schema(description = "좌표 기반 업로드 시 생성할 장소 이름", example = "새로운 핀 장소")
        String placeName,

        @Size(max = 255, message = "장소 주소는 255자 이하여야 합니다.")
        @Schema(description = "좌표 기반 업로드 시 생성할 장소 주소", example = "경상남도 진주시 핀좌표로 1")
        String address,

        @Size(max = 50, message = "장소 카테고리는 50자 이하여야 합니다.")
        @Schema(description = "좌표 기반 업로드 시 생성할 장소 카테고리", example = "풍경")
        String category,

        @Schema(description = "좌표 기반 업로드 시 사용할 좌표 토큰", example = "c8b65c4a-8181-4d3b-b83f-a48b82d10f2c")
        String coordinateToken,

        @NotNull(message = "파일은 필수입니다.")
        @Schema(description = "업로드할 게시글 첨부 파일", type = "string", format = "binary")
        MultipartFile file
) {
    @AssertTrue(message = "장소 ID, 카카오 장소 ID 또는 좌표 기반 장소 정보는 필수입니다.")
    public boolean isValidPlace() {
        return hasExistingPlaceReference() || hasCoordinateBasedPlacePayload();
    }

    @AssertTrue(message = "좌표 기반 업로드 시 장소 이름은 필수입니다.")
    public boolean isValidPlaceName() {
        return !requiresCoordinateBasedPlace() || StringUtils.hasText(placeName);
    }

    @AssertTrue(message = "좌표 기반 업로드 시 장소 주소는 필수입니다.")
    public boolean isValidAddress() {
        return !requiresCoordinateBasedPlace() || StringUtils.hasText(address);
    }

    @AssertTrue(message = "좌표 기반 업로드 시 장소 카테고리는 필수입니다.")
    public boolean isValidCategory() {
        return !requiresCoordinateBasedPlace() || StringUtils.hasText(category);
    }

    @AssertTrue(message = "좌표 기반 업로드 시 좌표 토큰은 필수입니다.")
    public boolean isValidCoordinateToken() {
        return !requiresCoordinateBasedPlace() || StringUtils.hasText(coordinateToken);
    }

    private boolean hasExistingPlaceReference() {
        return StringUtils.hasText(kakaoPlaceId) || placeId != null;
    }

    private boolean hasCoordinateBasedPlacePayload() {
        return StringUtils.hasText(coordinateToken)
                && StringUtils.hasText(placeName)
                && StringUtils.hasText(address)
                && StringUtils.hasText(category);
    }

    private boolean requiresCoordinateBasedPlace() {
        return !hasExistingPlaceReference()
                && (StringUtils.hasText(placeName)
                || StringUtils.hasText(address)
                || StringUtils.hasText(category)
                || StringUtils.hasText(coordinateToken));
    }
}
