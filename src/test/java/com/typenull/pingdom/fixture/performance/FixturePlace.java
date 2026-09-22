package com.typenull.pingdom.fixture.performance;

import com.typenull.pingdom.place.application.service.place.PlaceSearchSort;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import java.util.Set;

/** 탐색·추천 부하 시나리오에 사용할 장소의 소유자·좌표·공개/운영/검증 상태·반응 지표와 정렬 기준을 담는다. */
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
