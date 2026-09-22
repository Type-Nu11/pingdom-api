package com.typenull.pingdom.identity.domain.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MerchantOwnerPlaceModelTest {

    /**
     * 소유 장소 생성 시 품질 UNMEASURED·응답/취소/노쇼 비율 0·미평가 시각을 기본값으로 갖는지 검증.
     */
    @Test
    void defaultsUnmeasuredOperationalQuality() {
        MerchantOwnerPlace place = place();

        assertThat(place.getOperationalQualityStatus()).isEqualTo(MerchantOperationalQualityStatus.UNMEASURED);
        assertThat(place.getReservationResponseRate()).isZero();
        assertThat(place.getReservationCancellationRate()).isZero();
        assertThat(place.getNoShowRate()).isZero();
        assertThat(place.getQualityEvaluatedAt()).isNull();
    }

    /**
     * 운영 품질 갱신에서 비율 경계 0·100을 허용하고 HEALTHY 상태·평가 시각을 보존하는지 검증.
     */
    @Test
    void acceptsQualityRateBoundaries() {
        MerchantOwnerPlace place = place();

        place.updateOperationalQuality(
                MerchantOperationalQualityStatus.HEALTHY,
                0,
                100,
                0,
                LocalDateTime.of(2026, 8, 4, 12, 0)
        );

        assertThat(place.getOperationalQualityStatus()).isEqualTo(MerchantOperationalQualityStatus.HEALTHY);
        assertThat(place.getReservationResponseRate()).isZero();
        assertThat(place.getReservationCancellationRate()).isEqualTo(100);
        assertThat(place.getNoShowRate()).isZero();
        assertThat(place.getQualityEvaluatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 4, 12, 0));
    }

    /**
     * 응답률 -1·취소율 101·노쇼율 101 입력이 각각 IllegalArgumentException으로 거절되는지 검증.
     */
    @Test
    void rejectsOutOfRangeQualityRates() {
        assertThatThrownBy(() -> place().updateOperationalQuality(
                MerchantOperationalQualityStatus.AT_RISK, -1, 0, 0, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> place().updateOperationalQuality(
                MerchantOperationalQualityStatus.AT_RISK, 0, 101, 0, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> place().updateOperationalQuality(
                MerchantOperationalQualityStatus.AT_RISK, 0, 0, 101, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 품질 enum이 미평가·정상·주의·위험 네 상태를 정해진 순서로 유지하는지 검증.
     */
    @Test
    void exposesPersistedQualityStatuses() {
        assertThat(MerchantOperationalQualityStatus.values())
                .containsExactly(
                        MerchantOperationalQualityStatus.UNMEASURED,
                        MerchantOperationalQualityStatus.HEALTHY,
                        MerchantOperationalQualityStatus.NEEDS_ATTENTION,
                        MerchantOperationalQualityStatus.AT_RISK
                );
    }

    /**
     * 점주 2의 장소 1 소유 관계를 만들어 품질 기본값·갱신 경계의 입력으로 제공.
     */
    private MerchantOwnerPlace place() {
        return MerchantOwnerPlace.builder()
                .placeId(1L)
                .merchantOwnerUserId(2L)
                .createdAt(LocalDateTime.of(2026, 8, 4, 10, 0))
                .build();
    }
}
