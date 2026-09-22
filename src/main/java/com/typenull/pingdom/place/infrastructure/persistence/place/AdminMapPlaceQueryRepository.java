package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 관리 화면의 장소명·영문명·주소·등록자 ID 검색과 관광 분류 일괄 조회를 제공.
 * 운영·탐색 공개 상태를 제한하지 않으며 숫자 키워드는 장소 ID가 아닌 등록자 userId와 비교.
 */
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
              AND (:hasCategory = false OR LOWER(TRIM(m.category)) IN :categoryAliases)
            """)
    Page<MapPlace> searchAdminPlaces(
            @Param("keyword") String keyword,
            @Param("numericKeyword") Long numericKeyword,
            @Param("hasCategory") boolean hasCategory,
            @Param("categoryAliases") Collection<String> categoryAliases,
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
