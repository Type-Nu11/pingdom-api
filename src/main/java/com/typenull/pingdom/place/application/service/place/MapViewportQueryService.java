package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.api.dto.place.map.MapClusterItem;
import com.typenull.pingdom.place.api.dto.place.map.MapMarkerItem;
import com.typenull.pingdom.place.api.dto.place.map.MapViewportResponse;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapViewportQueryRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MapViewportQueryService {

    static final int MARKER_ZOOM_THRESHOLD = 14;
    static final int MAX_RESULT_SIZE = 500;

    private final MapViewportQueryRepository mapViewportQueryRepository;

    @Transactional(readOnly = true)
    public MapViewportResponse find(double west, double south, double east, double north, int zoom) {
        validate(west, south, east, north, zoom);

        if (zoom >= MARKER_ZOOM_THRESHOLD) {
            List<MapMarkerItem> results = mapViewportQueryRepository.findMarkers(
                    west, south, east, north, MAX_RESULT_SIZE + 1
            );
            boolean truncated = results.size() > MAX_RESULT_SIZE;
            List<MapMarkerItem> markers = truncated
                    ? List.copyOf(results.subList(0, MAX_RESULT_SIZE))
                    : results;
            return new MapViewportResponse("MARKERS", zoom, List.of(), markers, truncated);
        }

        double cellSize = gridCellSize(west, south, east, north, zoom);
        List<MapClusterItem> results = mapViewportQueryRepository.findClusters(
                west, south, east, north, cellSize, MAX_RESULT_SIZE + 1
        );
        boolean truncated = results.size() > MAX_RESULT_SIZE;
        List<MapClusterItem> clusters = truncated
                ? List.copyOf(results.subList(0, MAX_RESULT_SIZE))
                : results;
        return new MapViewportResponse("CLUSTERS", zoom, clusters, List.of(), truncated);
    }

    private void validate(double west, double south, double east, double north, int zoom) {
        if (!Double.isFinite(west) || !Double.isFinite(south)
                || !Double.isFinite(east) || !Double.isFinite(north)
                || west < -180d || east > 180d || south < -90d || north > 90d
                || west >= east || south >= north || zoom < 0 || zoom > 20) {
            throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
        }
    }

    private double gridCellSize(double west, double south, double east, double north, int zoom) {
        int divisions = Math.max(8, Math.min(64, 1 << Math.max(0, zoom - 3)));
        return Math.max((east - west) / divisions, (north - south) / divisions);
    }
}
