package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityPlaceDailyView;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 사용자·장소·날짜 유일 조합에 PostgreSQL 충돌 무시 삽입을 수행합니다.
 * 반환값 1은 최초 기록, 0은 기존 기록이며 날짜의 KST 변환은 호출 서비스에서 수행합니다.
 */
public interface CommunityPlaceDailyViewRepository extends JpaRepository<CommunityPlaceDailyView, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO community_place_daily_view (user_id, map_place_id, viewed_on)
            VALUES (:userId, :placeId, :viewedOn)
            ON CONFLICT (user_id, map_place_id, viewed_on) DO NOTHING
            """, nativeQuery = true)
    int insertIgnoreDuplicate(
            @Param("userId") long userId,
            @Param("placeId") long placeId,
            @Param("viewedOn") LocalDate viewedOn
    );
}
