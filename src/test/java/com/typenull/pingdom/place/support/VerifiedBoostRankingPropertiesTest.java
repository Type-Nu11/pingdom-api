package com.typenull.pingdom.place.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VerifiedBoostRankingPropertiesTest {

    /** NaN과 상한 초과 0.26 점수를 구성 단계에서 거부하는지 확인. */
    @Test
    void rejectsInvalidBoostScore() {
        assertThatThrownBy(() -> new VerifiedBoostRankingProperties(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VerifiedBoostRankingProperties(0.26d))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
