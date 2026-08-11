package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface MapPlaceCoordinateQueryRepository extends Repository<MapPlace, Long> {

    @Query(
            value = """
                    SELECT m
                    FROM MapPlace m
                    WHERE m.latitude IS NOT NULL
                      AND m.longitude IS NOT NULL
                    ORDER BY m.latitude ASC, m.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(m)
                    FROM MapPlace m
                    WHERE m.latitude IS NOT NULL
                      AND m.longitude IS NOT NULL
                    """
    )
    Page<MapPlace> findCoordinatePage(Pageable pageable);
}
