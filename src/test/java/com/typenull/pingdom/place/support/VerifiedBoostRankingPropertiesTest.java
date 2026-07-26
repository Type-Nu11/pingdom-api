package com.typenull.pingdom.place.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VerifiedBoostRankingPropertiesTest {

    @Test
    void rejectsNonFiniteOrOutOfRangeScore() {
        assertThatThrownBy(() -> new VerifiedBoostRankingProperties(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VerifiedBoostRankingProperties(0.26d))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
