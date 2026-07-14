package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPublicResponse;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantOwnerPublicQueryService {

    private final MerchantOwnerPlaceRepository placeRepository;
    private final MerchantOwnerProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public MerchantOwnerPublicResponse findByPlaceId(Long placeId) {
        return placeRepository.findById(placeId)
                .flatMap(mapping -> profileRepository.findById(mapping.getMerchantOwnerUserId()))
                .filter(profile -> profile.getStatus() == MerchantOwnerStatus.ACTIVE)
                .map(MerchantOwnerPublicResponse::from)
                .orElse(null);
    }
}
