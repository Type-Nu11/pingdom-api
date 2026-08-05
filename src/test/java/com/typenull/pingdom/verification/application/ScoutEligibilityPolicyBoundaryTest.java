package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.verification.domain.ScoutProfile;
import com.typenull.pingdom.verification.infrastructure.ScoutActivityEligibilityRepository;
import com.typenull.pingdom.verification.infrastructure.ScoutProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScoutEligibilityPolicyBoundaryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 12, 0);

    private final ScoutActivityEligibilityRepository eligibilityRepository =
            mock(ScoutActivityEligibilityRepository.class);
    private final ScoutProfileRepository profileRepository = mock(ScoutProfileRepository.class);
    private final ScoutEligibilityPolicy policy = new ScoutEligibilityPolicy(
            eligibilityRepository,
            profileRepository,
            Clock.fixed(Instant.parse("2026-08-05T03:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void activeProfileWithoutEligibilityFailsClosed() {
        ScoutProfile profile = ScoutProfile.pending(1L, "Scout", null, NOW);
        profile.activate(9L, NOW);
        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(eligibilityRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(policy.isEligible(1L)).isFalse();
    }
}
