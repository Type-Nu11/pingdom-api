package com.typenull.pingdom.offer.infrastructure;

import com.typenull.pingdom.offer.domain.TouristCoupon;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

/**
 * 사용자별 발급 쿠폰을 조회하고 사용 코드의 쓰기 잠금을 제공.
 * 코드 정규화와 소유권 검증은 서비스가 담당하며, 만료 상태는 엔티티와 조회 조건에서 시각으로 계산.
 */
public interface TouristCouponRepository extends JpaRepository<TouristCoupon, Long>, JpaSpecificationExecutor<TouristCoupon> {

    boolean existsByOfferIdAndUserId(Long offerId, Long userId);

    Optional<TouristCoupon> findByIdAndUserId(Long id, Long userId);

    Page<TouristCoupon> findAllByUserId(Long userId, Pageable pageable);

    List<TouristCoupon> findAllByUserIdOrderByIssuedAtDescIdDesc(Long userId);

    @Modifying
    @Query("DELETE FROM TouristCoupon coupon WHERE coupon.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT coupon FROM TouristCoupon coupon WHERE coupon.code = :code")
    Optional<TouristCoupon> findByCodeForUpdate(@Param("code") String code);
}
