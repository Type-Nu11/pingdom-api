package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.place.TouristCategory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AdminMapPlaceQueryRepository extends Repository<MapPlace, Long> {

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE (:keyword IS NULL OR :keyword = ''
                   OR LOWER(m.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(m.englishName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<MapPlace> findByNameContaining(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            SELECT m
            FROM MapPlace m
            WHERE (:keyword IS NULL OR :keyword = ''
                   OR LOWER(m.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(m.englishName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(m.address) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR (:numericKeyword IS NOT NULL AND m.userId = :numericKeyword))
            """)
    Page<MapPlace> searchAdminPlaces(
            @Param("keyword") String keyword,
            @Param("numericKeyword") Long numericKeyword,
            Pageable pageable
    );

    @Query("""
            SELECT m.id AS placeId, touristCategory AS touristCategory
            FROM MapPlace m
            JOIN m.touristCategories touristCategory
            WHERE m.id IN :placeIds
            ORDER BY m.id, touristCategory
            """)
    List<PlaceTouristCategoryProjection> findTouristCategoriesByPlaceIds(
            @Param("placeIds") Collection<Long> placeIds
    );

    interface PlaceTouristCategoryProjection {
        Long getPlaceId();

        TouristCategory getTouristCategory();
    }
}
