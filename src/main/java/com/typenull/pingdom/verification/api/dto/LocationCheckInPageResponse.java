package com.typenull.pingdom.verification.api.dto;

import java.util.List;

/** 본인 체크인 목록과 페이지 정보. page는 1부터 시작하고 totalElements는 전체 체크인 수. */
public record LocationCheckInPageResponse(List<LocationCheckInResponse> items, int page, int limit,
        long totalElements, int totalPages, boolean hasNext) {}
