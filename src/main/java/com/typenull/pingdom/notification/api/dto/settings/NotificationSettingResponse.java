package com.typenull.pingdom.notification.api.dto.settings;

import com.typenull.pingdom.notification.domain.NotificationSetting;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;

@Schema(description = "알림 수신 설정 응답")
public record NotificationSettingResponse(
        @Schema(description = "핫플레이스 알림 수신 여부", example = "true")
        boolean newHotplaceEnabled,

        @Schema(description = "좋아요 알림 수신 여부", example = "true")
        boolean newLikeEnabled,

        @Schema(description = "quiet hours 적용 여부", example = "false")
        boolean quietHoursEnabled,

        @Schema(description = "quiet hours 시작 시각", example = "22:00:00")
        LocalTime quietHoursStart,

        @Schema(description = "quiet hours 종료 시각", example = "08:00:00")
        LocalTime quietHoursEnd,

        @Schema(description = "IANA timezone", example = "Asia/Seoul")
        String timezone
) {
    public static NotificationSettingResponse from(NotificationSetting setting) {
        return new NotificationSettingResponse(
                setting.isNewHotplaceEnabled(),
                setting.isNewLikeEnabled(),
                setting.isQuietHoursEnabled(),
                setting.getQuietHoursStart(),
                setting.getQuietHoursEnd(),
                setting.getTimezone()
        );
    }
}
