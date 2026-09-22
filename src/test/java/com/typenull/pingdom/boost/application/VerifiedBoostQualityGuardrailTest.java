package com.typenull.pingdom.boost.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.boost.domain.exception.VerifiedBoostErrorCode;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOperationalQualityStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VerifiedBoostQualityGuardrailTest {

    private final MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
    private final VerifiedBoostQualityGuardrail guardrail = new VerifiedBoostQualityGuardrail(placeRepository);

    /**
     * HEALTHY인 소유 장소가 운영 중이며 탐색에 공개되어 있으면 품질 검사가 예외 없이 통과하는지 검증.
     */
    @Test
    void acceptsHealthyVisibleOperatingPlace() {
        MerchantOwnerPlace ownerPlace = ownerPlace(MerchantOperationalQualityStatus.HEALTHY);
        MapPlace place = mock(MapPlace.class);
        when(place.isOperating()).thenReturn(true);
        when(place.isVisibleInDiscovery()).thenReturn(true);
        when(placeRepository.findById(2L)).thenReturn(Optional.of(place));

        guardrail.requireEligible(ownerPlace);
    }

    /**
     * 운영 품질이 AT_RISK인 장소는 QUALITY_GUARDRAIL_BLOCKED로 거절되는지 검증.
     */
    @Test
    void unhealthyPlaceIsBlocked() {
        MerchantOwnerPlace ownerPlace = ownerPlace(MerchantOperationalQualityStatus.AT_RISK);

        assertThatThrownBy(() -> guardrail.requireEligible(ownerPlace))
                .isInstanceOfSatisfying(VerifiedBoostException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(VerifiedBoostErrorCode.QUALITY_GUARDRAIL_BLOCKED));
    }

    /**
     * 지정한 운영 품질 상태를 가진 점주 1의 장소 2를 만들어 품질 조건을 분리해 검증.
     */
    private MerchantOwnerPlace ownerPlace(MerchantOperationalQualityStatus status) {
        return MerchantOwnerPlace.builder()
                .placeId(2L)
                .merchantOwnerUserId(1L)
                .operationalQualityStatus(status)
                .createdAt(LocalDateTime.of(2026, 7, 26, 12, 0))
                .build();
    }
}
