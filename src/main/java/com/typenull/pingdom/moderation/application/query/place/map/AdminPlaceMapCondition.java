package com.typenull.pingdom.moderation.application.query.place.map;

import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapRequest;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.domain.place.category.PlaceCategoryPolicy;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;

/** 토큰에도 원래 조회 조건을 담아 후속 페이지에서 필터와 영역이 바뀌지 않게 한다. */
public record AdminPlaceMapCondition(double west, double south, double east, double north,
                                     String keyword, String category,
                                     PlaceDiscoveryStatus discoveryStatus, PlaceOperatingStatus operatingStatus) {
    public static final double MAX_LATITUDE = 85.05112878;

    public static AdminPlaceMapCondition from(AdminPlaceMapRequest r) {
        if (r.west() == null || r.south() == null || r.east() == null || r.north() == null
                || r.zoom() == null || r.zoom() < 0 || r.zoom() > 20
                || r.width() == null || r.width() < 64 || r.width() > 8192
                || r.height() == null || r.height() < 64 || r.height() > 8192
                || (r.keyword() != null && r.keyword().length() > 100)
                || (r.category() != null && r.category().length() > 100)) {
            throw new AdminException(AdminErrorCode.ADMIN_PLACE_MAP_INVALID_REQUEST);
        }
        var condition = new AdminPlaceMapCondition(r.west(), r.south(), r.east(), r.north(),
                r.keyword() == null ? "" : r.keyword().trim(), PlaceCategoryPolicy.normalize(r.category()),
                r.discoveryStatus(), r.operatingStatus());
        condition.validate();
        return condition;
    }

    public void validate() {
        if (!Double.isFinite(west) || !Double.isFinite(south) || !Double.isFinite(east) || !Double.isFinite(north)
                || west < -180 || east > 180 || south < -MAX_LATITUDE || north > MAX_LATITUDE
                || west >= east || south >= north || keyword == null || keyword.length() > 100) {
            throw new AdminException(AdminErrorCode.ADMIN_PLACE_MAP_INVALID_REQUEST);
        }
    }
}
