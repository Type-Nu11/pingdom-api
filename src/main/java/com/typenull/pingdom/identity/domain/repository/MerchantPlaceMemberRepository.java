package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantPlaceMemberRepository extends JpaRepository<MerchantPlaceMember, Long> {
    List<MerchantPlaceMember> findAllByPlaceIdAndStatusOrderByIdAsc(Long placeId, com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberStatus status);
    Optional<MerchantPlaceMember> findByPlaceIdAndUserId(Long placeId, Long userId);

    List<MerchantPlaceMember> findAllByPlaceId(Long placeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select member from MerchantPlaceMember member where member.placeId = :placeId and member.userId = :userId")
    Optional<MerchantPlaceMember> findByPlaceIdAndUserIdForUpdate(
            @Param("placeId") Long placeId, @Param("userId") Long userId);
}
