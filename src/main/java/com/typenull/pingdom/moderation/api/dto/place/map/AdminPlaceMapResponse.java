package com.typenull.pingdom.moderation.api.dto.place.map;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/** 지역 전체 집계와 viewport 공간 집계는 서로 합산하지 않는다. */
@Schema(description = "관리자 지도. 지역 전체 count와 viewportPlaceCount는 별도 범위이며 합산하지 않습니다.")
public record AdminPlaceMapResponse(
        @Schema(allowableValues = {"SIDO", "SIGUNGU", "SPATIAL"}) String mode, int zoom, List<Region> regions, List<Cluster> clusters, List<Marker> markers,
        @Schema(description = "현재 화면 안의 동일 필터·유효 좌표 장소 수. 지역 전체 count와 합산하지 않습니다.") long viewportPlaceCount,
        @Schema(description = "모든 표시 대상을 누락 없이 표현했는지 여부") boolean complete,
        @Schema(nullable = true, allowableValues = {"RESULT_BUDGET_EXCEEDED"}) String incompleteReason,
        @Schema(nullable = true, allowableValues = {"ZOOM_IN"}) String recoveryAction
) {
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
