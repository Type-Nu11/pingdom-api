package com.typenull.pingdom.place.api.dto.registration;

import java.util.List;

public record PlaceRegistrationPageResponse(List<PlaceRegistrationResponse> applications, int page, int limit,
                                            long totalCount, int totalPages, boolean hasNext) {
}
