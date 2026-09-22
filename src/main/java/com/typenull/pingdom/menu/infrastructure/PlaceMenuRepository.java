package com.typenull.pingdom.menu.infrastructure;

import com.typenull.pingdom.menu.domain.PlaceMenu;
import com.typenull.pingdom.menu.domain.PlaceMenuStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/**
 * 표시 순서와 ID로 메뉴 정렬을 고정하고 개별 변경 또는 전체 재정렬용 쓰기 잠금을 제공합니다.
 * 잠금 조회의 사용 가능 여부와 수명은 호출 트랜잭션에 종속됩니다.
 */
public interface PlaceMenuRepository extends JpaRepository<PlaceMenu, Long> {
    List<PlaceMenu> findAllByPlaceIdOrderByDisplayOrderAscIdAsc(Long placeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select menu from PlaceMenu menu where menu.placeId = :placeId order by menu.displayOrder asc, menu.id asc")
    List<PlaceMenu> findAllByPlaceIdOrderByDisplayOrderAscIdAscForUpdate(@Param("placeId") Long placeId);

    List<PlaceMenu> findAllByPlaceIdAndStatusInOrderByDisplayOrderAscIdAsc(Long placeId,
            Collection<PlaceMenuStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select menu from PlaceMenu menu where menu.id = :id")
    Optional<PlaceMenu> findByIdForUpdate(@Param("id") Long id);
}
