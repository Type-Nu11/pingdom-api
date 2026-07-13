package com.typenull.pingdom.identity.api.dto.export;

import com.typenull.pingdom.identity.application.query.UserDataExportResult;
import com.typenull.pingdom.identity.domain.travel.CurrentActivityIntent;
import com.typenull.pingdom.identity.domain.travel.TravelScheduleState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "사용자 데이터 내보내기 응답")
public record UserDataExportResponse(
        @Schema(description = "사용자 기본 정보")
        ExportUserResponse user,
        @Schema(description = "사용자 북마크 전체 목록")
        List<ExportBookmarkResponse> bookmarks,
        @Schema(description = "최근 좋아요한 지도 이미지 ID 목록. 최대 50개")
        List<Long> likedMapImageIds,
        @Schema(description = "사용자 여행 일정 목록")
        List<ExportTravelScheduleResponse> travelSchedules,
        @Schema(description = "만료되지 않은 현재 행동 의도. 없으면 null", nullable = true)
        ExportCurrentActivityIntentResponse currentActivityIntent
) {

    public static UserDataExportResponse from(UserDataExportResult result) {
        return new UserDataExportResponse(
                ExportUserResponse.from(result.user()),
                result.bookmarks().stream()
                        .map(ExportBookmarkResponse::from)
                        .toList(),
                result.likedMapImageIds(),
                result.travelSchedules().stream()
                        .map(ExportTravelScheduleResponse::from)
                        .toList(),
                ExportCurrentActivityIntentResponse.from(result.currentActivityIntent())
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

    public record ExportTravelScheduleResponse(
            @Schema(description = "여행 일정 ID", example = "1")
            Long id,
            @Schema(description = "여행 시작일", example = "2026-08-01")
            LocalDate startDate,
            @Schema(description = "여행 종료일", example = "2026-08-04")
            LocalDate endDate,
            @Schema(description = "저장된 일정 상태", example = "SCHEDULED")
            TravelScheduleState state
    ) {

        private static ExportTravelScheduleResponse from(UserDataExportResult.ExportTravelSchedule schedule) {
            return new ExportTravelScheduleResponse(
                    schedule.id(),
                    schedule.startDate(),
                    schedule.endDate(),
                    schedule.state()
            );
        }
    }

    public record ExportCurrentActivityIntentResponse(
            @Schema(description = "현재 행동 의도", example = "CAFE")
            CurrentActivityIntent intent,
            @Schema(description = "행동 의도 만료 시각")
            LocalDateTime expiresAt
    ) {

        private static ExportCurrentActivityIntentResponse from(
                UserDataExportResult.ExportCurrentActivityIntent currentActivityIntent
        ) {
            if (currentActivityIntent == null) {
                return null;
            }
            return new ExportCurrentActivityIntentResponse(
                    currentActivityIntent.intent(),
                    currentActivityIntent.expiresAt()
            );
        }
    }
}
