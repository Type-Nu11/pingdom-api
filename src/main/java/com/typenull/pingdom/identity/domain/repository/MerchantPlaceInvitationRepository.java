package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitation;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitationStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantPlaceInvitationRepository extends JpaRepository<MerchantPlaceInvitation, Long> {
    boolean existsByPlaceIdAndInviteeUserIdAndStatus(Long placeId, Long inviteeUserId, MerchantPlaceInvitationStatus status);
    Optional<MerchantPlaceInvitation> findByIdAndPlaceId(Long id, Long placeId);
}
