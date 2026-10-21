package com.typenull.pingdom.moderation.application.query.place.map;

import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapRequest;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.Cluster;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.GroupPage;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.Region;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.infrastructure.persistence.place.AdminPlaceMapQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.AdminPlaceMapQueryRepository.Cell;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 지역 count와 viewport count를 분리하고 제한 초과 시 격자를 넓혀 재집계한다. */
@Service
@RequiredArgsConstructor
public class AdminPlaceMapQueryService {
    private static final double WORLD_METERS = 40075016.68557849;
    private final AdminPlaceMapQueryRepository repository;
    private final AdminPlaceMapClusterToken tokens;
    private final AdminPlaceMapProperties properties;

    // 집계 후 마커/그룹 검증 사이의 동시 변경으로 응답 내 개수가 어긋나지 않도록 동일 스냅샷을 사용한다.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminPlaceMapResponse find(AdminPlaceMapRequest request) {
        var condition = AdminPlaceMapCondition.from(request);
        int maxItems = properties.maxItems();
        try {
            repository.setQueryTimeout();
            long viewportCount = repository.countViewport(condition);
            String mode = request.zoom() <= 8 ? "SIDO" : request.zoom() <= 11 ? "SIGUNGU" : "SPATIAL";
            if (viewportCount == 0) return response(mode, request.zoom(), List.of(), List.of(), List.of(), 0);
            List<Region> regions = "SPATIAL".equals(mode) ? List.of() : repository.findRegions(condition, mode, maxItems + 1);
            // 지역만으로 예산을 소진하면 전체 viewport를 공간 모드로 바꿔 누락 없이 묶는다.
            if (regions.size() >= maxItems) {
                regions = List.of();
                mode = "SPATIAL";
            }
            boolean unknownOnly = !"SPATIAL".equals(mode);
            int screenBudget = Math.max(4, Math.min(maxItems, (int) ((long) request.width() * request.height() / (64 * 64))));
            int budget = Math.max(1, Math.min(maxItems - regions.size(), screenBudget));
            double cellSize = WORLD_METERS / Math.scalb(1d, request.zoom()) / 4d;
            List<Cell> cells = List.of();
            // 최대 세계 크기의 두 배 셀까지 유한하게 재집계한다. LIMIT은 초과 감지용이며 결과 절단용이 아니다.
            for (int attempt = 0; attempt < 24; attempt++) {
                cells = repository.findCells(condition, cellSize, unknownOnly, budget + 1);
                if (cells.size() <= budget) break;
                cellSize *= 2;
            }
            if (cells.size() > budget) {
                return new AdminPlaceMapResponse(mode, request.zoom(), List.of(), List.of(), List.of(),
                        viewportCount, false, "RESULT_BUDGET_EXCEEDED", "ZOOM_IN");
            }
            // MIN(id)는 모든 그룹에 대해 한 번에 조회한다. 하위 셀은 부모 셀의 부분집합이므로
            // 기존 최소 ID가 남아 있는 하위 그룹에서는 같은 장소가 계속 대표가 된다.
            var representatives = repository.markers(cells.stream().map(Cell::representativeId).toList());
            var byId = representatives.stream().collect(java.util.stream.Collectors.toMap(
                    AdminPlaceMapResponse.Marker::id, java.util.function.Function.identity()));
            var clusters = new ArrayList<Cluster>();
            var singleIds = new ArrayList<Long>();
            for (Cell cell : cells) {
                if (cell.count() == 1) {
                    singleIds.add(cell.representativeId());
                } else {
                    boolean sameCoordinate = cell.bounds().west() == cell.bounds().east()
                            && cell.bounds().south() == cell.bounds().north();
                    String token = tokens.issue(condition, cellSize, cell.x(), cell.y(), unknownOnly, cell.fingerprint());
                    clusters.add(new Cluster(tokens.displayKey(condition, cellSize, cell.x(), cell.y(), unknownOnly), token, cell.count(), java.util.Objects.requireNonNull(byId.get(cell.representativeId()), "그룹 대표 장소 누락"), cell.latitude(), cell.longitude(), cell.bounds(),
                            "VIEWPORT", request.zoom() == 20 || sameCoordinate ? "LIST" : "EXPAND",
                            request.zoom() == 20 || sameCoordinate ? null : request.zoom() + 1));
                }
            }
            // 대표는 그룹 안에만 반환하며 단일 장소 셀만 최상위 markers에 포함한다.
            var singleIdSet = new java.util.HashSet<>(singleIds);
            return response(mode, request.zoom(), regions, clusters,
                    representatives.stream().filter(marker -> singleIdSet.contains(marker.id())).toList(), viewportCount);
        } catch (DataAccessException e) {
            throw queryFailure(e);
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GroupPage groupPlaces(String token, int page, int limit) {
        if (page < 1 || page > 1000000 || limit < 1 || limit > 100) {
            throw new AdminException(AdminErrorCode.ADMIN_PLACE_MAP_INVALID_REQUEST);
        }
        var p = tokens.read(token);
        try {
            repository.setQueryTimeout();
            var state = repository.groupState(p.condition(), p.cellSize(), p.x(), p.y(), p.unknownOnly());
            if (state.count() < 2 || !p.fingerprint().equals(state.fingerprint())) {
                throw new AdminException(AdminErrorCode.ADMIN_PLACE_MAP_CLUSTER_INVALID);
            }
            long pages = (state.count() + limit - 1) / limit;
            var places = repository.groupPlaces(p.condition(), p.cellSize(), p.x(), p.y(), p.unknownOnly(), page, limit);
            return new GroupPage(places, page, limit, state.count(), pages, page < pages);
        } catch (DataAccessException e) {
            throw queryFailure(e);
        }
    }

    private RuntimeException queryFailure(DataAccessException exception) {
        // PostgreSQL 57014는 드라이버 설정에 따라 QueryTimeoutException 이외의 예외로도 변환된다.
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof QueryTimeoutException
                    || cause instanceof java.sql.SQLException sql && "57014".equals(sql.getSQLState())) {
                return new AdminException(AdminErrorCode.ADMIN_PLACE_MAP_QUERY_TIMEOUT, exception);
            }
        }
        return exception;
    }

    private AdminPlaceMapResponse response(String mode, int zoom, List<Region> regions, List<Cluster> clusters,
            List<AdminPlaceMapResponse.Marker> markers, long total) {
        return new AdminPlaceMapResponse(mode, zoom, regions, clusters, markers, total, true, null, null);
    }
}
