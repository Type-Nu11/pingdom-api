package com.typenull.pingdom.product.infrastructure;

import com.typenull.pingdom.product.domain.ReservableProduct;
import com.typenull.pingdom.product.domain.ReservableProductStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

/**
 * 상품과 현재 장소 소유 관계를 기준으로 점주 목록을 조회합니다.
 * 현재 소유자 조회는 상품에 기록된 최초 점주 ID가 아니라 MerchantOwnerPlace 관계를 사용합니다.
 */
public interface ReservableProductRepository extends JpaRepository<ReservableProduct, Long> {
    Optional<ReservableProduct> findByIdAndPlaceIdAndStatus(Long id, Long placeId, ReservableProductStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from ReservableProduct product where product.id = :id")
    Optional<ReservableProduct> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select product from ReservableProduct product
            where exists (
                select ownerPlace.placeId from MerchantOwnerPlace ownerPlace
                where ownerPlace.placeId = product.placeId
                  and ownerPlace.merchantOwnerUserId = :ownerId
            )
            order by product.createdAt desc, product.id desc
            """)
    List<ReservableProduct> findAllCurrentlyOwned(@Param("ownerId") Long ownerId);
}
