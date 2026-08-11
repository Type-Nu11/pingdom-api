package com.typenull.pingdom.place.api.dto.place.operating.notice;

import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNotice;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeSeverity;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeStatus;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "상점 운영 상태 공지 응답")
public record PlaceOperatingNoticeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long placeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PlaceOperatingNoticeType noticeType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PlaceOperatingNoticeSeverity severity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PlaceOperatingNoticeStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime startsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime expiresAt,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime expiredAt,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime canceledAt,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String cancelReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean visibleNow
) {

    public static PlaceOperatingNoticeResponse from(PlaceOperatingNotice notice, LocalDateTime checkedAt) {
        return new PlaceOperatingNoticeResponse(
                notice.getId(),
                notice.getPlace().getId(),
                notice.getNoticeType(),
                notice.getSeverity(),
                notice.getStatus(),
                notice.getMessage(),
                notice.getStartsAt(),
                notice.getExpiresAt(),
                notice.getExpiredAt(),
                notice.getCanceledAt(),
                notice.getCancelReason(),
                notice.isVisibleAt(checkedAt)
        );
    }
}
