package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.api.dto.place.map.MapClusterItem;
import com.typenull.pingdom.place.api.dto.place.map.MapMarkerItem;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MapViewportQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<MapClusterItem> findClusters(
            double west,
            double south,
            double east,
            double north,
            double cellSize,
            int limit
    ) {
        return jdbcTemplate.query("""
                SELECT
                    FLOOR(mp.longitude / :cellSize)::bigint AS grid_x,
                    FLOOR(mp.latitude / :cellSize)::bigint AS grid_y,
                    AVG(mp.latitude) AS latitude,
                    AVG(mp.longitude) AS longitude,
                    COUNT(*) AS place_count
                FROM map_place mp
                WHERE mp.location && ST_MakeEnvelope(:west, :south, :east, :north, 4326)
                  AND mp.operating_status = 'OPERATING'
                  AND mp.discovery_status = 'VISIBLE'
                GROUP BY grid_x, grid_y
                ORDER BY place_count DESC, grid_y, grid_x
                LIMIT :limit
                """, Map.of(
                "west", west,
                "south", south,
                "east", east,
                "north", north,
                "cellSize", cellSize,
                "limit", limit
        ), (resultSet, rowNumber) -> new MapClusterItem(
                resultSet.getLong("grid_x") + ":" + resultSet.getLong("grid_y"),
                resultSet.getDouble("latitude"),
                resultSet.getDouble("longitude"),
                resultSet.getLong("place_count")
        ));
    }

    public List<MapMarkerItem> findMarkers(
            double west,
            double south,
            double east,
            double north,
            int limit
    ) {
        return jdbcTemplate.query("""
                SELECT
                    mp.map_place_id,
                    mp.place_name,
                    mp.category,
                    mp.image_url,
                    mp.latitude,
                    mp.longitude,
                    mp.photo_count
                FROM map_place mp
                WHERE mp.location && ST_MakeEnvelope(:west, :south, :east, :north, 4326)
                  AND mp.operating_status = 'OPERATING'
                  AND mp.discovery_status = 'VISIBLE'
                ORDER BY mp.photo_count DESC, mp.map_place_id DESC
                LIMIT :limit
                """, Map.of(
                "west", west,
                "south", south,
                "east", east,
                "north", north,
                "limit", limit
        ), (resultSet, rowNumber) -> new MapMarkerItem(
                resultSet.getLong("map_place_id"),
                resultSet.getString("place_name"),
                resultSet.getString("category"),
                resultSet.getString("image_url"),
                resultSet.getDouble("latitude"),
                resultSet.getDouble("longitude"),
                resultSet.getLong("photo_count")
        ));
    }
}
