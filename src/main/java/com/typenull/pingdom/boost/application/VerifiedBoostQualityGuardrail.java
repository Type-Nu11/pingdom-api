package com.typenull.pingdom.boost.application;

import com.typenull.pingdom.boost.domain.exception.VerifiedBoostErrorCode;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOperationalQualityStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 신규 부스트 집행 전에 장소 소유 관계의 HEALTHY 품질 상태와 장소의 운영·탐색 노출 상태를 검사합니다.
 * 이 검사는 조회 시점의 조건이며 이후 품질 변화나 추천 결과 노출을 보장하지 않습니다.
 */
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
