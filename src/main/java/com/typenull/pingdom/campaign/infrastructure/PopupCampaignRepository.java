package com.typenull.pingdom.campaign.infrastructure;

import com.typenull.pingdom.campaign.domain.PopupCampaign;
import com.typenull.pingdom.campaign.domain.PopupCampaignStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PopupCampaignRepository extends JpaRepository<PopupCampaign, Long> {

    Page<PopupCampaign> findAllByMerchantOwnerUserId(Long ownerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT campaign FROM PopupCampaign campaign WHERE campaign.id = :id AND campaign.merchantOwnerUserId = :ownerId")
    Optional<PopupCampaign> findOwnedByIdForUpdate(@Param("id") Long id, @Param("ownerId") Long ownerId);

    @Query("""
            SELECT campaign FROM PopupCampaign campaign
            WHERE campaign.status = :status
              AND campaign.startsAt <= :now
              AND campaign.endsAt > :now
              AND (:placeId IS NULL OR campaign.placeId = :placeId)
              AND EXISTS (
                  SELECT ownerPlace.placeId FROM MerchantOwnerPlace ownerPlace
                  WHERE ownerPlace.placeId = campaign.placeId
                    AND ownerPlace.merchantOwnerUserId = campaign.merchantOwnerUserId
              )
              AND EXISTS (
                  SELECT profile.userId FROM MerchantOwnerProfile profile
                  WHERE profile.userId = campaign.merchantOwnerUserId
                    AND profile.status = com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus.ACTIVE
              )
              AND EXISTS (
                  SELECT verification.userId FROM MerchantVerification verification
                  WHERE verification.userId = campaign.merchantOwnerUserId
                    AND verification.identityStatus = com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus.APPROVED
                    AND verification.businessStatus = com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus.APPROVED
              )
              AND EXISTS (
                  SELECT ownerUser.id FROM User ownerUser
                  WHERE ownerUser.id = campaign.merchantOwnerUserId
                    AND ownerUser.role = com.typenull.pingdom.identity.domain.UserRole.MERCHANT_OWNER
                    AND ownerUser.status = com.typenull.pingdom.identity.domain.UserStatus.ACTIVE
                    AND (ownerUser.banned = false OR (
                        ownerUser.banType = com.typenull.pingdom.identity.domain.UserBanType.TEMPORARY
                        AND ownerUser.banExpiresAt <= :now
                    ))
              )
            """)
    Page<PopupCampaign> findDiscoverable(
            @Param("status") PopupCampaignStatus status,
            @Param("now") LocalDateTime now,
            @Param("placeId") Long placeId,
            Pageable pageable
    );

    @Query("""
            SELECT campaign FROM PopupCampaign campaign
            WHERE campaign.id = :id
              AND campaign.status = :status
              AND campaign.startsAt <= :now
              AND campaign.endsAt > :now
              AND EXISTS (
                  SELECT ownerPlace.placeId FROM MerchantOwnerPlace ownerPlace
                  WHERE ownerPlace.placeId = campaign.placeId
                    AND ownerPlace.merchantOwnerUserId = campaign.merchantOwnerUserId
              )
              AND EXISTS (
                  SELECT profile.userId FROM MerchantOwnerProfile profile
                  WHERE profile.userId = campaign.merchantOwnerUserId
                    AND profile.status = com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus.ACTIVE
              )
              AND EXISTS (
                  SELECT verification.userId FROM MerchantVerification verification
                  WHERE verification.userId = campaign.merchantOwnerUserId
                    AND verification.identityStatus = com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus.APPROVED
                    AND verification.businessStatus = com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus.APPROVED
              )
              AND EXISTS (
                  SELECT ownerUser.id FROM User ownerUser
                  WHERE ownerUser.id = campaign.merchantOwnerUserId
                    AND ownerUser.role = com.typenull.pingdom.identity.domain.UserRole.MERCHANT_OWNER
                    AND ownerUser.status = com.typenull.pingdom.identity.domain.UserStatus.ACTIVE
                    AND (ownerUser.banned = false OR (
                        ownerUser.banType = com.typenull.pingdom.identity.domain.UserBanType.TEMPORARY
                        AND ownerUser.banExpiresAt <= :now
                    ))
              )
            """)
    Optional<PopupCampaign> findDiscoverableById(
            @Param("id") Long id,
            @Param("status") PopupCampaignStatus status,
            @Param("now") LocalDateTime now
    );
}
