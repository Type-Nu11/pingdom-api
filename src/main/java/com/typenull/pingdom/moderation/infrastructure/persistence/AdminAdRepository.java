package com.typenull.pingdom.moderation.infrastructure.persistence;

import com.typenull.pingdom.moderation.domain.ad.AdminAd;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAdRepository extends JpaRepository<AdminAd, Long> {

    @Query(value = """
            SELECT a FROM AdminAd a
            WHERE (:hasKeyword = false OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:hasStartedFrom = false OR a.startAt >= :startedFrom)
              AND (:hasStartedTo = false OR a.startAt <= :startedTo)
              AND (:hasDisplayStatus = false OR
                   (:scheduled = true AND a.startAt > :now) OR
                   (:active = true AND a.startAt <= :now AND a.endAt > :now) OR
                   (:expired = true AND a.endAt <= :now))
            """,
            countQuery = """
            SELECT COUNT(a) FROM AdminAd a
            WHERE (:hasKeyword = false OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:hasStartedFrom = false OR a.startAt >= :startedFrom)
              AND (:hasStartedTo = false OR a.startAt <= :startedTo)
              AND (:hasDisplayStatus = false OR
                   (:scheduled = true AND a.startAt > :now) OR
                   (:active = true AND a.startAt <= :now AND a.endAt > :now) OR
                   (:expired = true AND a.endAt <= :now))
            """)
    Page<AdminAd> findAdminAds(
            @Param("hasKeyword") boolean hasKeyword, @Param("keyword") String keyword,
            @Param("hasStartedFrom") boolean hasStartedFrom, @Param("startedFrom") LocalDateTime startedFrom,
            @Param("hasStartedTo") boolean hasStartedTo, @Param("startedTo") LocalDateTime startedTo,
            @Param("hasDisplayStatus") boolean hasDisplayStatus,
            @Param("scheduled") boolean scheduled,
            @Param("active") boolean active,
            @Param("expired") boolean expired,
            @Param("now") LocalDateTime now, Pageable pageable);
}
