package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNotice;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * 공지 변경용 잠금 조회와 게시 시작·만료 대상 조회를 제공합니다.
 * 생명주기 대상 쿼리는 시간·ID 순으로 정렬하며 Pageable이 없어 한 호출의 조회 건수 제한이 없습니다.
 */
public interface PlaceOperatingNoticeRepository extends JpaRepository<PlaceOperatingNotice, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT notice
            FROM PlaceOperatingNotice notice
            JOIN FETCH notice.place
            WHERE notice.id = :id
            """)
    Optional<PlaceOperatingNotice> findByIdForUpdate(@Param("id") Long id);

    List<PlaceOperatingNotice> findAllByPlace_IdAndStatusInOrderByStartsAtAscIdAsc(
            Long placeId,
            Collection<PlaceOperatingNoticeStatus> statuses
    );

    List<PlaceOperatingNotice> findAllByPlace_IdOrderByStartsAtAscIdAsc(Long placeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT notice
            FROM PlaceOperatingNotice notice
            WHERE notice.status = :status
              AND notice.startsAt <= :now
              AND notice.expiresAt > :now
            ORDER BY notice.startsAt ASC, notice.id ASC
            """)
    List<PlaceOperatingNotice> findActivatableNoticesForUpdate(
            @Param("status") PlaceOperatingNoticeStatus status,
            @Param("now") LocalDateTime now
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
