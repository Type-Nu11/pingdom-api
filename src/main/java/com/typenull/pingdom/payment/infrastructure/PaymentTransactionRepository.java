package com.typenull.pingdom.payment.infrastructure;

import com.typenull.pingdom.payment.domain.PaymentTransaction;
import com.typenull.pingdom.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByTouristUserIdAndIdempotencyKey(Long touristUserId, String idempotencyKey);

    Optional<PaymentTransaction> findFirstByReservationIdAndStatusIn(Long reservationId,
            Collection<PaymentStatus> statuses);

    Page<PaymentTransaction> findAllByTouristUserId(Long touristUserId, Pageable pageable);

    Page<PaymentTransaction> findAllByMerchantOwnerUserId(Long merchantOwnerUserId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from PaymentTransaction payment where payment.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") Long id);
}
