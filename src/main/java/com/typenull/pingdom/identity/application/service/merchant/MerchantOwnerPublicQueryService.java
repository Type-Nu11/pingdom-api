package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPublicResponse;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 소유 매핑에 연결된 ACTIVE 사업자 프로필만 공개 응답으로 반환합니다.
 * 매핑이나 활성 프로필이 없으면 null이며 본인·사업자 검증 승인 여부는 이 조회에서 추가 검사하지 않습니다.
 */
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
