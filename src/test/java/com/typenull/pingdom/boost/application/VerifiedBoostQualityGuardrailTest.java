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

    @Test
    void healthyVisibleOperatingPlaceIsEligible() {
        MerchantOwnerPlace ownerPlace = ownerPlace(MerchantOperationalQualityStatus.HEALTHY);
        MapPlace place = mock(MapPlace.class);
        when(place.isOperating()).thenReturn(true);
        when(place.isVisibleInDiscovery()).thenReturn(true);
        when(placeRepository.findById(2L)).thenReturn(Optional.of(place));

        guardrail.requireEligible(ownerPlace);
    }

    @Test
    void unhealthyPlaceIsBlocked() {
        MerchantOwnerPlace ownerPlace = ownerPlace(MerchantOperationalQualityStatus.AT_RISK);

        assertThatThrownBy(() -> guardrail.requireEligible(ownerPlace))
                .isInstanceOfSatisfying(VerifiedBoostException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(VerifiedBoostErrorCode.QUALITY_GUARDRAIL_BLOCKED));
    }

    private MerchantOwnerPlace ownerPlace(MerchantOperationalQualityStatus status) {
        return MerchantOwnerPlace.builder()
                .placeId(2L)
                .merchantOwnerUserId(1L)
                .operationalQualityStatus(status)
                .createdAt(LocalDateTime.of(2026, 7, 26, 12, 0))
                .build();
    }
}
