package com.typenull.pingdom.reservation.infrastructure;

import com.typenull.pingdom.reservation.domain.ReservationQuote;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 생성·재전송은 호출자가 관광객 사용자 행을 먼저 잠가 직렬화. */
public interface ReservationQuoteRepository extends JpaRepository<ReservationQuote, String> {
    long countByTouristUserIdAndCreatedAtAfter(Long touristUserId, Instant cutoff);

    long countByTouristUserIdAndIdempotencyKeyIsNull(Long touristUserId);

    Optional<ReservationQuote> findByTouristUserIdAndIdempotencyKey(Long touristUserId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select quote from ReservationQuote quote where quote.id = :id")
    Optional<ReservationQuote> findByIdForUpdate(@Param("id") String id);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 2)
    @Query(value = """
            delete from reservation_quote where id in (
                select id from reservation_quote
                where idempotency_key is null and created_at < :cutoff
                order by created_at limit 1000 for update skip locked
            )
            """, nativeQuery = true)
    int deleteUnusedBefore(@Param("cutoff") Instant cutoff);
}
