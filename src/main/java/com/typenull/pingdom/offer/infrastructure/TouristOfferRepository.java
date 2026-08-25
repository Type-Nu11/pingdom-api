package com.typenull.pingdom.offer.infrastructure;

import com.typenull.pingdom.offer.domain.OfferStatus;
import com.typenull.pingdom.offer.domain.TouristOffer;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface TouristOfferRepository extends JpaRepository<TouristOffer, Long> {

    Page<TouristOffer> findAllByMerchantOwnerUserId(Long merchantOwnerUserId, Pageable pageable);

    List<TouristOffer> findAllByMerchantOwnerUserIdOrderByCreatedAtDescIdDesc(Long merchantOwnerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT offer
            FROM TouristOffer offer
            WHERE offer.merchantOwnerUserId = :merchantOwnerUserId
              AND offer.placeId = :placeId
            ORDER BY offer.id ASC
            """)
    List<TouristOffer> findAllByMerchantOwnerUserIdAndPlaceIdForUpdate(
            @Param("merchantOwnerUserId") Long merchantOwnerUserId,
            @Param("placeId") Long placeId
    );

    Optional<TouristOffer> findByIdAndMerchantOwnerUserId(Long id, Long merchantOwnerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT offer
            FROM TouristOffer offer
            WHERE offer.id = :offerId
              AND offer.merchantOwnerUserId = :merchantOwnerUserId
            """)
    Optional<TouristOffer> findOwnedByIdForUpdate(
            @Param("offerId") Long offerId,
            @Param("merchantOwnerUserId") Long merchantOwnerUserId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT offer FROM TouristOffer offer WHERE offer.id = :offerId")
    Optional<TouristOffer> findByIdForUpdate(@Param("offerId") Long offerId);

    @Query("""
            SELECT offer
            FROM TouristOffer offer
            WHERE offer.status = :status
              AND offer.startsAt <= :now
              AND offer.endsAt > :now
              AND (
                  offer.inventoryPolicy = com.typenull.pingdom.offer.domain.CouponInventoryPolicy.UNLIMITED
                  OR offer.issuedQuantity < offer.totalQuantity
              )
              AND (:placeId IS NULL OR offer.placeId = :placeId)
              AND EXISTS (
                  SELECT ownerPlace.placeId
                  FROM MerchantOwnerPlace ownerPlace
                  WHERE ownerPlace.placeId = offer.placeId
                    AND ownerPlace.merchantOwnerUserId = offer.merchantOwnerUserId
              )
              AND EXISTS (
                  SELECT profile.userId
                  FROM MerchantOwnerProfile profile
                  WHERE profile.userId = offer.merchantOwnerUserId
                    AND profile.status = com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus.ACTIVE
              )
              AND EXISTS (
                  SELECT verification.userId
                  FROM MerchantVerification verification
                  WHERE verification.userId = offer.merchantOwnerUserId
                    AND verification.identityStatus = com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus.APPROVED
                    AND verification.businessStatus = com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus.APPROVED
              )
              AND EXISTS (
                  SELECT ownerUser.id
                  FROM User ownerUser
                  WHERE ownerUser.id = offer.merchantOwnerUserId
                    AND ownerUser.role = com.typenull.pingdom.identity.domain.UserRole.MERCHANT_OWNER
                    AND ownerUser.status = com.typenull.pingdom.identity.domain.UserStatus.ACTIVE
                    AND (
                        ownerUser.banned = false
                        OR (
                            ownerUser.banType = com.typenull.pingdom.identity.domain.UserBanType.TEMPORARY
                            AND ownerUser.banExpiresAt <= :now
                        )
                    )
              )
            """)
    Page<TouristOffer> findAvailable(
            @Param("status") OfferStatus status,
            @Param("now") LocalDateTime now,
            @Param("placeId") Long placeId,
            Pageable pageable
    );

    @Query("""
            SELECT offer
            FROM TouristOffer offer
            WHERE offer.id = :offerId
              AND offer.status = :status
              AND offer.startsAt <= :now
              AND offer.endsAt > :now
              AND (
                  offer.inventoryPolicy = com.typenull.pingdom.offer.domain.CouponInventoryPolicy.UNLIMITED
                  OR offer.issuedQuantity < offer.totalQuantity
              )
              AND EXISTS (
                  SELECT ownerPlace.placeId
                  FROM MerchantOwnerPlace ownerPlace
                  WHERE ownerPlace.placeId = offer.placeId
                    AND ownerPlace.merchantOwnerUserId = offer.merchantOwnerUserId
              )
              AND EXISTS (
                  SELECT profile.userId
                  FROM MerchantOwnerProfile profile
                  WHERE profile.userId = offer.merchantOwnerUserId
                    AND profile.status = com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus.ACTIVE
              )
              AND EXISTS (
                  SELECT verification.userId
                  FROM MerchantVerification verification
                  WHERE verification.userId = offer.merchantOwnerUserId
                    AND verification.identityStatus = com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus.APPROVED
                    AND verification.businessStatus = com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus.APPROVED
              )
              AND EXISTS (
                  SELECT ownerUser.id
                  FROM User ownerUser
                  WHERE ownerUser.id = offer.merchantOwnerUserId
                    AND ownerUser.role = com.typenull.pingdom.identity.domain.UserRole.MERCHANT_OWNER
                    AND ownerUser.status = com.typenull.pingdom.identity.domain.UserStatus.ACTIVE
                    AND (
                        ownerUser.banned = false
                        OR (
                            ownerUser.banType = com.typenull.pingdom.identity.domain.UserBanType.TEMPORARY
                            AND ownerUser.banExpiresAt <= :now
                        )
                    )
              )
            """)
    Optional<TouristOffer> findAvailableById(
            @Param("offerId") Long offerId,
            @Param("status") OfferStatus status,
            @Param("now") LocalDateTime now
    );

    @Query("""
            SELECT DISTINCT offer.placeId
            FROM TouristOffer offer
            WHERE offer.placeId IN :placeIds
              AND offer.status = com.typenull.pingdom.offer.domain.OfferStatus.PUBLISHED
              AND offer.startsAt <= :now
              AND offer.endsAt > :now
              AND (
                  offer.inventoryPolicy = com.typenull.pingdom.offer.domain.CouponInventoryPolicy.UNLIMITED
                  OR offer.issuedQuantity < offer.totalQuantity
              )
              AND EXISTS (
                  SELECT ownerPlace.placeId FROM MerchantOwnerPlace ownerPlace
                  WHERE ownerPlace.placeId = offer.placeId
                    AND ownerPlace.merchantOwnerUserId = offer.merchantOwnerUserId
              )
              AND EXISTS (
                  SELECT profile.userId FROM MerchantOwnerProfile profile
                  WHERE profile.userId = offer.merchantOwnerUserId
                    AND profile.status = com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus.ACTIVE
              )
              AND EXISTS (
                  SELECT verification.userId FROM MerchantVerification verification
                  WHERE verification.userId = offer.merchantOwnerUserId
                    AND verification.identityStatus = com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus.APPROVED
                    AND verification.businessStatus = com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus.APPROVED
              )
              AND EXISTS (
                  SELECT ownerUser.id FROM User ownerUser
                  WHERE ownerUser.id = offer.merchantOwnerUserId
                    AND ownerUser.role = com.typenull.pingdom.identity.domain.UserRole.MERCHANT_OWNER
                    AND ownerUser.status = com.typenull.pingdom.identity.domain.UserStatus.ACTIVE
                    AND (ownerUser.banned = false OR (
                        ownerUser.banType = com.typenull.pingdom.identity.domain.UserBanType.TEMPORARY
                        AND ownerUser.banExpiresAt <= :now
                    ))
              )
            """)
    List<Long> findPlaceIdsWithAvailableOffers(
            @Param("placeIds") Collection<Long> placeIds,
            @Param("now") LocalDateTime now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TouristOffer offer
            SET offer.status = com.typenull.pingdom.offer.domain.OfferStatus.CLOSED,
                offer.updatedAt = :now,
                offer.version = offer.version + 1
            WHERE offer.merchantOwnerUserId = :merchantOwnerUserId
              AND offer.status <> com.typenull.pingdom.offer.domain.OfferStatus.CLOSED
            """)
    int closeAllByMerchantOwnerUserId(
            @Param("merchantOwnerUserId") Long merchantOwnerUserId,
            @Param("now") LocalDateTime now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TouristOffer offer
            SET offer.status = com.typenull.pingdom.offer.domain.OfferStatus.CLOSED,
                offer.updatedAt = :now,
                offer.version = offer.version + 1
            WHERE offer.merchantOwnerUserId = :merchantOwnerUserId
              AND offer.placeId IN :placeIds
              AND offer.status <> com.typenull.pingdom.offer.domain.OfferStatus.CLOSED
            """)
    int closeAllByMerchantOwnerUserIdAndPlaceIdIn(
            @Param("merchantOwnerUserId") Long merchantOwnerUserId,
            @Param("placeIds") Collection<Long> placeIds,
            @Param("now") LocalDateTime now
    );
}
