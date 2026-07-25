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
            """)
    Page<PopupCampaign> findDiscoverable(
            @Param("status") PopupCampaignStatus status,
            @Param("now") LocalDateTime now,
            @Param("placeId") Long placeId,
            Pageable pageable
    );

    Optional<PopupCampaign> findByIdAndStatusAndStartsAtLessThanEqualAndEndsAtAfter(
            Long id,
            PopupCampaignStatus status,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    );
}
