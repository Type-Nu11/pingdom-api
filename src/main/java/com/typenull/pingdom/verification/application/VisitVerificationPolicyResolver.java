package com.typenull.pingdom.verification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 장소별 override를 우선하고, 없으면 전역 기본 체류 인증 정책을 적용합니다. */
@Component
@RequiredArgsConstructor
public class VisitVerificationPolicyResolver {
    private final VisitVerificationProperties properties;

    public VisitVerificationPolicy resolve(Long placeId) {
        return new VisitVerificationPolicy(properties.radiusMetersFor(placeId), properties.dwellDuration());
    }
}
