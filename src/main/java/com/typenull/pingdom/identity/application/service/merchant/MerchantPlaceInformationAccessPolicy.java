package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMemberRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 장소 추가 정보 API의 접근 조건을 PLACE_INFO_EDIT capability에 연결.
 * 조회에도 같은 조건을 적용하므로 일반 열람 권한만 가진 팀원은 사용 불가.
 */
@Component
public class MerchantPlaceInformationAccessPolicy {

    private final MerchantPlaceCapabilityPolicy capabilityPolicy;

    @Autowired
    public MerchantPlaceInformationAccessPolicy(MerchantPlaceCapabilityPolicy capabilityPolicy) {
        this.capabilityPolicy = capabilityPolicy;
    }

    public MerchantPlaceInformationAccessPolicy(
            MerchantPlaceMemberRepository memberRepository,
            MerchantOwnerPlaceRepository ownerPlaceRepository,
            UserRepository userRepository,
            Clock clock) {
        this(new MerchantPlaceCapabilityPolicy(memberRepository, ownerPlaceRepository, userRepository, clock));
    }

    public void requireManager(Long actorId, Long placeId) {
        capabilityPolicy.require(actorId, placeId, MerchantPlaceCapability.PLACE_INFO_EDIT);
    }
}
