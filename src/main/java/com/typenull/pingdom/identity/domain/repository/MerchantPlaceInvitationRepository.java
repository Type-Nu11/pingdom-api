package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitation;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitationStatus;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantPlaceInvitationRepository extends JpaRepository<MerchantPlaceInvitation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation from MerchantPlaceInvitation invitation
            where invitation.placeId = :placeId and invitation.inviteeUserId = :inviteeUserId
              and invitation.status = :status
            """)
    Optional<MerchantPlaceInvitation> findByPlaceIdAndInviteeUserIdAndStatusForUpdate(
            @Param("placeId") Long placeId,
            @Param("inviteeUserId") Long inviteeUserId,
            @Param("status") MerchantPlaceInvitationStatus status);

    Optional<MerchantPlaceInvitation> findByIdAndPlaceId(Long id, Long placeId);

    List<MerchantPlaceInvitation> findAllByPlaceIdAndStatus(
            Long placeId, MerchantPlaceInvitationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from MerchantPlaceInvitation invitation where invitation.id = :id")
    Optional<MerchantPlaceInvitation> findByIdForUpdate(@Param("id") Long id);
}
