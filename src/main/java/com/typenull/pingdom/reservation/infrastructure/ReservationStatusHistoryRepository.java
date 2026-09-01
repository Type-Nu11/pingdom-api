package com.typenull.pingdom.reservation.infrastructure;

import com.typenull.pingdom.reservation.domain.ReservationStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationStatusHistoryRepository extends JpaRepository<ReservationStatusHistory, Long> {
    List<ReservationStatusHistory> findAllByReservationIdOrderByChangedAtAscIdAsc(Long reservationId);
}
