package com.typenull.pingdom.moderation.infrastructure.persistence;

import com.typenull.pingdom.moderation.domain.ad.AdminAd;
import org.springframework.data.jpa.repository.JpaRepository;
import com.typenull.pingdom.moderation.domain.ad.AdminAdDisplayStatus;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAdRepository extends JpaRepository<AdminAd, Long> {

    @Query(value = """
            SELECT a FROM AdminAd a
            WHERE (:keyword IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:startedFrom IS NULL OR a.startAt >= :startedFrom)
              AND (:startedTo IS NULL OR a.startAt <= :startedTo)
              AND (:displayStatus IS NULL OR
                   (:displayStatus = com.typenull.pingdom.moderation.domain.ad.AdminAdDisplayStatus.SCHEDULED AND a.startAt > :now) OR
                   (:displayStatus = com.typenull.pingdom.moderation.domain.ad.AdminAdDisplayStatus.ACTIVE AND a.startAt <= :now AND a.endAt > :now) OR
                   (:displayStatus = com.typenull.pingdom.moderation.domain.ad.AdminAdDisplayStatus.EXPIRED AND a.endAt <= :now))
            """,
            countQuery = """
            SELECT COUNT(a) FROM AdminAd a
            WHERE (:keyword IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:startedFrom IS NULL OR a.startAt >= :startedFrom)
              AND (:startedTo IS NULL OR a.startAt <= :startedTo)
              AND (:displayStatus IS NULL OR
                   (:displayStatus = com.typenull.pingdom.moderation.domain.ad.AdminAdDisplayStatus.SCHEDULED AND a.startAt > :now) OR
                   (:displayStatus = com.typenull.pingdom.moderation.domain.ad.AdminAdDisplayStatus.ACTIVE AND a.startAt <= :now AND a.endAt > :now) OR
                   (:displayStatus = com.typenull.pingdom.moderation.domain.ad.AdminAdDisplayStatus.EXPIRED AND a.endAt <= :now))
            """)
    Page<AdminAd> findAdminAds(@Param("keyword") String keyword, @Param("displayStatus") AdminAdDisplayStatus displayStatus,
            @Param("startedFrom") LocalDateTime startedFrom, @Param("startedTo") LocalDateTime startedTo,
            @Param("now") LocalDateTime now, Pageable pageable);
}
