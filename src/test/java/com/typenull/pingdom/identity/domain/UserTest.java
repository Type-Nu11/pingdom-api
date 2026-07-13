package com.typenull.pingdom.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void replacesTravelPurposesDefensivelyAndClearsThemOnWithdrawal() {
        User user = User.builder()
                .travelPurposes(new LinkedHashSet<>(Set.of(TravelPurpose.K_POP)))
                .build();
        Set<TravelPurpose> updatedPurposes = new LinkedHashSet<>(Set.of(
                TravelPurpose.FOOD,
                TravelPurpose.EXHIBITION
        ));

        user.replaceTravelPurposes(updatedPurposes);
        updatedPurposes.clear();

        assertThat(user.currentTravelPurposes())
                .containsExactlyInAnyOrder(TravelPurpose.FOOD, TravelPurpose.EXHIBITION);
        assertThatThrownBy(() -> user.currentTravelPurposes().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        user.withdraw("withdrawn_user", "withdrawn@example.com", "encoded-password", LocalDateTime.now());

        assertThat(user.currentTravelPurposes()).isEmpty();
    }
}
