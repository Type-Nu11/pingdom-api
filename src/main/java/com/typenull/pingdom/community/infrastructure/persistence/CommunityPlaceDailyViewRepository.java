package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityPlaceDailyView;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
