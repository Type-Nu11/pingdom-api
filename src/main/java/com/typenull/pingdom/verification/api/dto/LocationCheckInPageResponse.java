package com.typenull.pingdom.verification.api.dto;

import java.util.List;

public record LocationCheckInPageResponse(List<LocationCheckInResponse> items, int page, int limit,
        long totalElements, int totalPages, boolean hasNext) {}
