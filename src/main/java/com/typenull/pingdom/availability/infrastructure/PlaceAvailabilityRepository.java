package com.typenull.pingdom.availability.infrastructure;

import com.typenull.pingdom.availability.domain.AvailabilityStatus;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceAvailabilityRepository extends JpaRepository<PlaceAvailability, Long> {
    Optional<PlaceAvailability> findByIdAndMerchantOwnerUserId(Long id, Long ownerId);

    @Query("""
            select availability from PlaceAvailability availability
            where availability.merchantOwnerUserId = :ownerId
              and exists (
                  select ownerPlace.placeId from MerchantOwnerPlace ownerPlace
                  where ownerPlace.placeId = availability.placeId
                    and ownerPlace.merchantOwnerUserId = :ownerId
              )
            order by availability.startsAt asc, availability.id asc
            """)
    List<PlaceAvailability> findAllCurrentlyOwned(@Param("ownerId") Long ownerId);

    @Query("""
            select availability from PlaceAvailability availability
            where availability.placeId = :placeId and availability.status = :status
              and availability.endsAt > :now and availability.remainingCapacity > 0
              and exists (
                  select ownerPlace.placeId from MerchantOwnerPlace ownerPlace
                  where ownerPlace.placeId = availability.placeId
                    and ownerPlace.merchantOwnerUserId = availability.merchantOwnerUserId
              )
              and exists (
                  select profile.userId from MerchantOwnerProfile profile
                  where profile.userId = availability.merchantOwnerUserId
                    and profile.status = com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus.ACTIVE
              )
              and exists (
                  select verification.userId from MerchantVerification verification
                  where verification.userId = availability.merchantOwnerUserId
                    and verification.identityStatus = com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus.APPROVED
                    and verification.businessStatus = com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus.APPROVED
              )
              and exists (
                  select ownerUser.id from User ownerUser
                  where ownerUser.id = availability.merchantOwnerUserId
                    and ownerUser.role = com.typenull.pingdom.identity.domain.UserRole.MERCHANT_OWNER
                    and ownerUser.status = com.typenull.pingdom.identity.domain.UserStatus.ACTIVE
                    and (ownerUser.banned = false or (
                        ownerUser.banType = com.typenull.pingdom.identity.domain.UserBanType.TEMPORARY
                        and ownerUser.banExpiresAt <= :now
                    ))
              )
            order by availability.startsAt asc, availability.id asc
            """)
    List<PlaceAvailability> findPublicByPlaceId(@Param("placeId") Long placeId,
            @Param("status") AvailabilityStatus status, @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select availability from PlaceAvailability availability where availability.id = :id")
    Optional<PlaceAvailability> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select availability from PlaceAvailability availability
            where availability.id = :id
              and exists (
                  select ownerPlace.placeId from MerchantOwnerPlace ownerPlace
                  where ownerPlace.placeId = availability.placeId
                    and ownerPlace.merchantOwnerUserId = availability.merchantOwnerUserId
              )
              and exists (
                  select profile.userId from MerchantOwnerProfile profile
                  where profile.userId = availability.merchantOwnerUserId
                    and profile.status = com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus.ACTIVE
              )
              and exists (
                  select verification.userId from MerchantVerification verification
                  where verification.userId = availability.merchantOwnerUserId
                    and verification.identityStatus = com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus.APPROVED
                    and verification.businessStatus = com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus.APPROVED
              )
              and exists (
                  select ownerUser.id from User ownerUser
                  where ownerUser.id = availability.merchantOwnerUserId
                    and ownerUser.role = com.typenull.pingdom.identity.domain.UserRole.MERCHANT_OWNER
                    and ownerUser.status = com.typenull.pingdom.identity.domain.UserStatus.ACTIVE
                    and (ownerUser.banned = false or (
                        ownerUser.banType = com.typenull.pingdom.identity.domain.UserBanType.TEMPORARY
                        and ownerUser.banExpiresAt <= :now
                    ))
              )
            """)
    Optional<PlaceAvailability> findReservableByIdForUpdate(@Param("id") Long id, @Param("now") LocalDateTime now);
}
