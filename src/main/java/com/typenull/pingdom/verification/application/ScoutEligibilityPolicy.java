package com.typenull.pingdom.verification.application;

import org.springframework.stereotype.Component;

@Component
public class ScoutEligibilityPolicy {

    public boolean isEligible(Long userId) {
        // #634의 Scout 자격 모델이 도입되기 전에는 일반 사용자를 Scout로 간주하지 않는다.
        // 자격 조회 경계가 준비되면 이 fail-closed 정책을 실제 자격 검증으로 교체한다.
        return false;
    }
}
