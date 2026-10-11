package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityPostDailyView;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 사용자·게시글·날짜 유일 조합에 PostgreSQL 충돌 무시 삽입을 수행.
 * 반환값 1은 최초 기록, 0은 기존 기록이며 날짜의 KST 변환은 호출 서비스에서 수행.
 */
public interface CommunityPostDailyViewRepository extends JpaRepository<CommunityPostDailyView, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO community_post_daily_view (user_id, community_post_id, viewed_on)
            VALUES (:userId, :postId, :viewedOn)
            ON CONFLICT (user_id, community_post_id, viewed_on) DO NOTHING
            """, nativeQuery = true)
    int insertIgnoreDuplicate(
            @Param("userId") long userId,
            @Param("postId") long postId,
            @Param("viewedOn") LocalDate viewedOn
    );
}
