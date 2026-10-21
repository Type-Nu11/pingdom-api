package com.typenull.pingdom.moderation.api.dto.place.map;

import java.util.List;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/** 영역 내 전체 마커 표시 가능 여부와 서버 적용 한도를 제공한다. */
@Schema(description = "관리자 지도 v2. 영역 수가 displayLimit 이하면 전체 마커, 초과하면 빈 배열과 확대 안내를 반환합니다.")
public record AdminPlaceMapResponse(
        @Schema(description = "호환 필드. v2에서는 zoom과 무관하게 SPATIAL입니다.", allowableValues = {"SIDO", "SIGUNGU", "SPATIAL"}) String mode,
        int zoom,
        @ArraySchema(arraySchema = @Schema(description = "호환 필드. v2에서는 항상 빈 배열입니다.", deprecated = true),
                schema = @Schema(implementation = Region.class)) List<Region> regions,
        @ArraySchema(arraySchema = @Schema(description = "호환 필드. v2에서는 항상 빈 배열입니다.", deprecated = true),
                schema = @Schema(implementation = Cluster.class)) List<Cluster> clusters,
        @ArraySchema(arraySchema = @Schema(description = "MARKERS이면 영역 내 전체 장소를 ID 오름차순으로 반환하고, ZOOM_REQUIRED이면 빈 배열입니다."),
                schema = @Schema(implementation = Marker.class)) List<Marker> markers,
        @Schema(description = "현재 화면 안의 동일 필터·유효 좌표 장소 수. 표시 한도와 관계없이 정확한 전체 수입니다.") long viewportPlaceCount,
        @Schema(description = "전체 개별 마커 표시 가능 여부. MARKERS이면 true, ZOOM_REQUIRED이면 false입니다.") boolean complete,
        @Schema(nullable = true, allowableValues = {"RESULT_BUDGET_EXCEEDED"}) String incompleteReason,
        @Schema(nullable = true, allowableValues = {"ZOOM_IN"}) String recoveryAction,
        @Schema(description = "서버에서 실제 적용한 전체 마커 표시 한도. 초기값 100. 클라이언트는 상향할 수 없습니다.", example = "100", minimum = "1", maximum = "100") int displayLimit,
        @Schema(description = "0건을 포함해 전체 표시 가능하면 MARKERS, 한도 초과로 확대가 필요하면 ZOOM_REQUIRED입니다.") DisplayStatus displayStatus
) {
    public enum DisplayStatus { MARKERS, ZOOM_REQUIRED }

    @Schema(name = "AdminPlaceMapBounds")
    public record Bounds(double west, double south, double east, double north) {}
    @Schema(name = "AdminPlaceMapRegion")
    public record Region(String regionLevel, String regionCode, String name, long count,
                         String countScope, double latitude, double longitude, Bounds bounds,
                         @Schema(allowableValues = {"PLACE_EXTENT"}) String boundsSource,
                         @Schema(allowableValues = {"EXPAND"}) String action, int nextZoom) {}
    @Schema(name = "AdminPlaceMapCluster")
    public record Cluster(String clusterKey, String clusterId, long count,
                          @Schema(description = "현재 영역·필터에 맞는 그룹 내 최소 ID 장소. 아이콘은 이 장소 좌표에 표시합니다. count는 대표를 포함하며 +N은 count-1입니다.",
                                  requiredMode = Schema.RequiredMode.REQUIRED) Marker representative,
                          double latitude, double longitude,
                          Bounds bounds, String countScope,
                          @Schema(allowableValues = {"EXPAND", "LIST"}) String action, Integer nextZoom) {}
    @Schema(name = "AdminPlaceMapMarker")
    public record Marker(long id, String name, double latitude, double longitude, String category,
                         String discoveryStatus, String operatingStatus) {}
    @Schema(name = "AdminPlaceMapGroupPage")
    public record GroupPage(List<Marker> places, int page, int limit, long totalCount,
                            long totalPages, boolean hasNext) {}
}
