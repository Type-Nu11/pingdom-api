package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNotice;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PlaceOperatingNoticeRepository extends JpaRepository<PlaceOperatingNotice, Long> {

    List<PlaceOperatingNotice> findAllByPlace_IdAndStatusInOrderByStartsAtAscIdAsc(
            Long placeId,
            Collection<PlaceOperatingNoticeStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT notice
            FROM PlaceOperatingNotice notice
            WHERE notice.status IN :statuses
              AND notice.expiresAt <= :now
            ORDER BY notice.expiresAt ASC, notice.id ASC
            """)
    List<PlaceOperatingNotice> findExpirableNoticesForUpdate(
            @Param("statuses") Collection<PlaceOperatingNoticeStatus> statuses,
            @Param("now") LocalDateTime now
    );
}
