package com.typenull.pingdom.place.api.dto.review;

import java.util.List;

public record AdminPlaceReviewDeletionRequestPageResponse(
        List<AdminPlaceReviewDeletionRequestResponse> deletionRequests,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {}
