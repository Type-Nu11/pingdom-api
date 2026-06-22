package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.MapPlace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AdminMapPlaceQueryRepository extends Repository<MapPlace, Long> {

    @Query("SELECT m FROM MapPlace m WHERE (:keyword IS NULL OR :keyword = '' OR m.name LIKE %:keyword%)")
    Page<MapPlace> findByNameContaining(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE (:keyword IS NULL OR :keyword = ''
                   OR m.name LIKE CONCAT('%', :keyword, '%')
                   OR m.address LIKE CONCAT('%', :keyword, '%')
                   OR (:numericKeyword IS NOT NULL AND m.userId = :numericKeyword))
            """)
    Page<MapPlace> searchAdminPlaces(
            @Param("keyword") String keyword,
            @Param("numericKeyword") Long numericKeyword,
            Pageable pageable
    );
}
