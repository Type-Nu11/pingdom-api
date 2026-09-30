package com.typenull.pingdom.place.application.service.place.operating;

import java.time.LocalDateTime;

/**
 * 한국 현지 영업 일정 평가 결과와 평가에 사용한 UTC 기준 시각. 저장된 장소 운영 상태와 구분되는 계산 결과.
 */
public record PlaceCurrentOperatingState(
        boolean currentlyOperating,
        LocalDateTime checkedAt
) {
}
