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
