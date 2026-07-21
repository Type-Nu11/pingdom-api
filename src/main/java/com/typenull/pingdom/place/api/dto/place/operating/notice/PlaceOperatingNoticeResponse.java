package com.typenull.pingdom.place.api.dto.place.operating.notice;

import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNotice;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeSeverity;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeStatus;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "상점 운영 상태 공지 응답")
public record PlaceOperatingNoticeResponse(
        Long id,
        Long placeId,
        PlaceOperatingNoticeType noticeType,
        PlaceOperatingNoticeSeverity severity,
        PlaceOperatingNoticeStatus status,
        String message,
        LocalDateTime startsAt,
        LocalDateTime expiresAt,
        @Schema(nullable = true)
        LocalDateTime expiredAt,
        @Schema(nullable = true)
        LocalDateTime canceledAt,
        @Schema(nullable = true)
        String cancelReason,
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
