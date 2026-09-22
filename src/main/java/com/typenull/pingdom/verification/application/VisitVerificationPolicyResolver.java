package com.typenull.pingdom.verification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 장소별 override를 우선하고, 없으면 전역 기본 체류 인증 정책을 적용합니다. */
@Component
@RequiredArgsConstructor
public class VisitVerificationPolicyResolver {
    private final VisitVerificationProperties properties;

    /**
     * 장소별 설정은 반경에만 적용하고 체류 시간은 전역 설정을 유지한 정책을 반환한다.
     * 장소 존재 여부를 조회하지 않으며, 반경 override가 없는 ID는 기본 반경을 사용한다.
     */
    public VisitVerificationPolicy resolve(Long placeId) {
        return new VisitVerificationPolicy(properties.radiusMetersFor(placeId), properties.dwellDuration());
    }
}
