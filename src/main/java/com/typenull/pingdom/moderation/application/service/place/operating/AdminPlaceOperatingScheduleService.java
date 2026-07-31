package com.typenull.pingdom.moderation.application.service.place.operating;

import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingScheduleUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingScheduleUpdateResponse;
import com.typenull.pingdom.moderation.application.service.place.quality.AdminMapPlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 정규 영업시간과 예외 일정 변경 유스케이스의 진입점이다. */
@Service
@RequiredArgsConstructor
public class AdminPlaceOperatingScheduleService {
    private final AdminMapPlaceService adminMapPlaceService;

    public AdminMapPlaceOperatingScheduleUpdateResponse updatePlaceOperatingSchedule(
            Long adminUserId, Long placeId, AdminMapPlaceOperatingScheduleUpdateRequest request
    ) {
        return adminMapPlaceService.updatePlaceOperatingSchedule(adminUserId, placeId, request);
    }
}
