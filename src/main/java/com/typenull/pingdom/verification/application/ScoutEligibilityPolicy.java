package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.verification.infrastructure.ScoutActivityEligibilityRepository;
import com.typenull.pingdom.verification.domain.ScoutProfileStatus;
import com.typenull.pingdom.verification.infrastructure.ScoutProfileRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 현재 시각의 활동 가능 여부를 활성 프로필과 활동 자격 기간의 교집합으로 판단.
 * 계정 역할·탈퇴·정지는 이 정책에서 조회하지 않으므로 호출 서비스가 별도로 검사.
 */
@Component
@RequiredArgsConstructor
public class ScoutEligibilityPolicy {

    private final ScoutActivityEligibilityRepository eligibilityRepository;
    private final ScoutProfileRepository profileRepository;
    private final Clock clock;

    /**
     * ID 누락·프로필 부재·비활성·활동 자격 부재 또는 기간 밖이면 false를 반환.
     * 프로필이 활성일 때만 자격 저장소를 조회하며 판정 시 상태 유지.
     */
    public boolean isEligible(Long userId) {
        return userId != null
                && profileRepository.findById(userId)
                .map(profile -> profile.getStatus() == ScoutProfileStatus.ACTIVE)
                .orElse(false)
                && eligibilityRepository.findById(userId)
                .map(eligibility -> eligibility.isEligibleAt(LocalDateTime.now(clock)))
                .orElse(false);
    }
}
