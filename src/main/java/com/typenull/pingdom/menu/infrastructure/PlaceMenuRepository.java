package com.typenull.pingdom.menu.infrastructure;

import com.typenull.pingdom.menu.domain.PlaceMenu;
import com.typenull.pingdom.menu.domain.PlaceMenuStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PlaceMenuRepository extends JpaRepository<PlaceMenu, Long> {
    List<PlaceMenu> findAllByPlaceIdOrderByDisplayOrderAscIdAsc(Long placeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select menu from PlaceMenu menu where menu.placeId = :placeId order by menu.displayOrder asc, menu.id asc")
    List<PlaceMenu> findAllByPlaceIdForUpdateOrderByDisplayOrderAscIdAsc(@Param("placeId") Long placeId);

    List<PlaceMenu> findAllByPlaceIdAndStatusInOrderByDisplayOrderAscIdAsc(Long placeId,
            Collection<PlaceMenuStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select menu from PlaceMenu menu where menu.id = :id")
    Optional<PlaceMenu> findByIdForUpdate(@Param("id") Long id);
}
