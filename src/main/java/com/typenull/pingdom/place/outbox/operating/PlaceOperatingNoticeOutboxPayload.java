package com.typenull.pingdom.place.outbox.operating;

import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNotice;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeSeverity;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeStatus;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeType;
import java.time.LocalDateTime;

public record PlaceOperatingNoticeOutboxPayload(
        Long noticeId,
        Long placeId,
        PlaceOperatingNoticeType noticeType,
        PlaceOperatingNoticeSeverity severity,
        PlaceOperatingNoticeStatus status,
        LocalDateTime startsAt,
        LocalDateTime expiresAt,
        LocalDateTime occurredAt
) {

    public static PlaceOperatingNoticeOutboxPayload from(PlaceOperatingNotice notice, LocalDateTime occurredAt) {
        return new PlaceOperatingNoticeOutboxPayload(
                notice.getId(),
                notice.getPlace().getId(),
                notice.getNoticeType(),
                notice.getSeverity(),
                notice.getStatus(),
                notice.getStartsAt(),
                notice.getExpiresAt(),
                occurredAt
        );
    }
}
