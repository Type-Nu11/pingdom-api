package com.typenull.pingdom.moderation.api.dto.ad;

import java.util.List;

public record AdminAdListResponse(List<AdminAdListItem> ads, int page, int limit,
        long totalCount, long totalPages, boolean hasNext) {}
