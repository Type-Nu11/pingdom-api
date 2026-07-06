package com.typenull.pingdom.moderation.domain;

import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;

public enum AdminPlaceSortParam {
    LATEST,
    OLDEST,
    LEVEL_DESC;

    public static AdminPlaceSortParam from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }

        try {
            return AdminPlaceSortParam.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new AdminException(AdminErrorCode.UNSUPPORTED_PLACE_SORT_PARAM, exception);
        }
    }
}
