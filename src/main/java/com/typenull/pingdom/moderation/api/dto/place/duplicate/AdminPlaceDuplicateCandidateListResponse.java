package com.typenull.pingdom.moderation.api.dto.place.duplicate;

import java.util.List;

public record AdminPlaceDuplicateCandidateListResponse(
        List<AdminPlaceDuplicateCandidateResponse> candidates,
        int page,
        int limit,
        long total,
        int totalPages,
        boolean hasNext
) {
}
