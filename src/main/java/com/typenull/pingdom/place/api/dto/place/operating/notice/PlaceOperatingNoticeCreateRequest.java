package com.typenull.pingdom.place.api.dto.place.operating.notice;

import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeSeverity;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Schema(description = "상점 운영 상태 공지 생성 요청")
public record PlaceOperatingNoticeCreateRequest(
        @NotNull
        PlaceOperatingNoticeType noticeType,
        @NotNull
        PlaceOperatingNoticeSeverity severity,
        @NotBlank
        @Size(max = 500)
        String message,
        @NotNull
        LocalDateTime startsAt,
        @NotNull
        @Future
        LocalDateTime expiresAt
) {
}
