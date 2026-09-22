package com.typenull.pingdom.moderation.infrastructure.persistence;

import com.typenull.pingdom.moderation.domain.ad.AdminAd;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 광고 시작 시각 필터는 양 끝을 포함하고 노출 상태는 시작 이상·종료 미만을 ACTIVE로 분류합니다.
 * 키워드는 대소문자를 구분하지 않는 LIKE 검색이며 서비스가 정규화한 입력과 필터 활성 플래그를 함께 받습니다.
 */
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
