package com.typenull.pingdom.moderation.application.query.place.map;

import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapRequest;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.DisplayStatus;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.GroupPage;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.Marker;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.infrastructure.persistence.place.AdminPlaceMapQueryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 현재 영역의 장소 수가 표시 한도 이하면 전체 마커를, 초과하면 확대 안내를 반환한다. */
@Service
@RequiredArgsConstructor
public class AdminPlaceMapQueryService {
    private final AdminPlaceMapQueryRepository repository;
    private final AdminPlaceMapClusterToken tokens;
    private final AdminPlaceMapProperties properties;

    // 집계와 마커 조회 사이의 동시 변경에도 개수와 배열이 일치하도록 동일 스냅샷을 사용한다.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminPlaceMapResponse find(AdminPlaceMapRequest request) {
        var condition = AdminPlaceMapCondition.from(request);
        int displayLimit = properties.displayLimit();
        try {
            repository.setQueryTimeout();
            long viewportCount = repository.countViewport(condition);
            if (viewportCount > displayLimit) {
                return new AdminPlaceMapResponse("SPATIAL", request.zoom(), List.of(), List.of(), List.of(),
                        viewportCount, false, "RESULT_BUDGET_EXCEEDED", "ZOOM_IN",
                        displayLimit, DisplayStatus.ZOOM_REQUIRED);
            }
            List<Marker> markers = viewportCount == 0 ? List.of()
                    : repository.findViewportMarkers(condition);
            return new AdminPlaceMapResponse("SPATIAL", request.zoom(), List.of(), List.of(), markers,
                    viewportCount, true, null, null, displayLimit, DisplayStatus.MARKERS);
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
}
