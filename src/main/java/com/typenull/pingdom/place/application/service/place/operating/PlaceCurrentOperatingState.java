package com.typenull.pingdom.place.application.service.place.operating;

import java.time.LocalDateTime;

/**
 * 영업 일정 평가 시점과 그 시점의 영업 여부. 저장된 장소 운영 상태와 구분되는 계산 결과.
 */
public record PlaceCurrentOperatingState(
        boolean currentlyOperating,
        LocalDateTime checkedAt
) {
}
