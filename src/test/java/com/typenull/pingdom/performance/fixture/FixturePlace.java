package com.typenull.pingdom.performance.fixture;

import com.typenull.pingdom.place.application.service.place.PlaceSearchSort;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import java.util.Set;

public record FixturePlace(
        long id,
        String name,
        long ownerUserId,
        double latitude,
        double longitude,
        PlaceOperatingStatus operatingStatus,
        PlaceDiscoveryStatus discoveryStatus,
        PlaceInformationSourceType informationSource,
        PlaceInformationVerificationStatus verificationStatus,
        Set<String> touristCategories,
        long photoCount,
        long bookmarkCount,
        long exposureCount,
        long clickCount,
        PlaceSearchSort expectedDefaultSort
) {
}
