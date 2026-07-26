package com.typenull.pingdom.boost.application;

import com.typenull.pingdom.boost.domain.exception.VerifiedBoostErrorCode;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOperationalQualityStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerifiedBoostQualityGuardrail {

    private final MapPlaceRepository placeRepository;

    public void requireEligible(MerchantOwnerPlace ownerPlace) {
        MapPlace place = placeRepository.findById(ownerPlace.getPlaceId()).orElse(null);
        if (ownerPlace.getOperationalQualityStatus() != MerchantOperationalQualityStatus.HEALTHY
                || place == null
                || !place.isOperating()
                || !place.isVisibleInDiscovery()) {
            throw new VerifiedBoostException(VerifiedBoostErrorCode.QUALITY_GUARDRAIL_BLOCKED);
        }
    }
}
