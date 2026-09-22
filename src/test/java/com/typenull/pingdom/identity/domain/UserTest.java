package com.typenull.pingdom.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserTest {

    /**
     * 여행 목적 교체 시 입력 집합 변경의 영향을 받지 않고 조회 집합도 수정 불가이며 탈퇴 시 목적이 비워지는지 검증한다.
     */
    @Test
    void protectsAndClearsTravelPurposes() {
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

    /**
     * 점주 권한 해제 시 역할을 USER로 변경하고 리프레시 토큰도 제거해 이전 권한의 갱신을 방지하는지 검증한다.
     */
    @Test
    void revocationClearsMerchantRefreshToken() {
        User user = User.builder()
                .role(UserRole.MERCHANT_OWNER)
                .refreshToken("refresh-token")
                .build();

        user.revokeMerchantOwnerRole();

        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getRefreshToken()).isNull();
    }
}
