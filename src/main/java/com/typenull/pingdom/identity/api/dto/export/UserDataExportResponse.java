package com.typenull.pingdom.identity.api.dto.export;

import com.typenull.pingdom.identity.application.query.UserDataExportResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "사용자 데이터 내보내기 응답")
public record UserDataExportResponse(
        @Schema(description = "사용자 기본 정보")
        ExportUserResponse user,
        @Schema(description = "사용자 북마크 전체 목록")
        List<ExportBookmarkResponse> bookmarks,
        @Schema(description = "최근 좋아요한 지도 이미지 ID 목록. 최대 50개")
        List<Long> likedMapImageIds
) {

    public static UserDataExportResponse from(UserDataExportResult result) {
        return new UserDataExportResponse(
                ExportUserResponse.from(result.user()),
                result.bookmarks().stream()
                        .map(ExportBookmarkResponse::from)
                        .toList(),
                result.likedMapImageIds()
        );
    }

    public record ExportUserResponse(
            @Schema(description = "사용자 ID", example = "1")
            Long id,
            @Schema(description = "사용자 아이디", example = "pingdom_user")
            String username,
            @Schema(description = "프로필 이미지 URL", example = "https://cdn.pingdom.com/profiles/user1.png", nullable = true)
            String profileImageUrl
    ) {

        private static ExportUserResponse from(UserDataExportResult.ExportUser user) {
            return new ExportUserResponse(user.id(), user.username(), user.profileImageUrl());
        }
    }

    public record ExportBookmarkResponse(
            @Schema(description = "북마크 ID", example = "10")
            Long id,
            @Schema(description = "북마크 대상 장소 ID", example = "123")
            Long placeId
    ) {

        private static ExportBookmarkResponse from(UserDataExportResult.ExportBookmark bookmark) {
            return new ExportBookmarkResponse(bookmark.id(), bookmark.placeId());
        }
    }
}
