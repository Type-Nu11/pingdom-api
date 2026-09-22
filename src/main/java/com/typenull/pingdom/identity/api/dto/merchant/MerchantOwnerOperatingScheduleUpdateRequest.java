package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingExceptionRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceRegularOperatingHourRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.Set;

/**
 * 정규 영업시간과 날짜별 예외 일정을 전체 교체하는 요청.
 * 각 필드를 생략하거나 null로 보내면 서비스에서 빈 집합으로 해석하므로 기존 해당 일정이 제거됨.
 */
@Schema(description = "Merchant Owner 장소 영업시간 변경 요청")
public record MerchantOwnerOperatingScheduleUpdateRequest(
        Set<@Valid AdminMapPlaceRegularOperatingHourRequest> regularHours,
        Set<@Valid AdminMapPlaceOperatingExceptionRequest> exceptions
) {
}
