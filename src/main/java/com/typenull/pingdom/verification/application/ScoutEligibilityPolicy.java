package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.verification.infrastructure.ScoutActivityEligibilityRepository;
import com.typenull.pingdom.verification.domain.ScoutProfileStatus;
import com.typenull.pingdom.verification.infrastructure.ScoutProfileRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScoutEligibilityPolicy {

    private final ScoutActivityEligibilityRepository eligibilityRepository;
    private final ScoutProfileRepository profileRepository;
    private final Clock clock;

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
