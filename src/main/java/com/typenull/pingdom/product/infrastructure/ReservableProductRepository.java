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
