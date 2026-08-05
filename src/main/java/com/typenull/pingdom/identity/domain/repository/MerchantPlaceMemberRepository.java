package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantPlaceMemberRepository extends JpaRepository<MerchantPlaceMember, Long> {
    List<MerchantPlaceMember> findAllByPlaceIdAndStatusOrderByIdAsc(Long placeId, com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberStatus status);
    Optional<MerchantPlaceMember> findByPlaceIdAndUserId(Long placeId, Long userId);
}
