package com.typenull.pingdom.moderation.application.service.place;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.moderation.application.service.place.quality.AdminMapPlaceService;
import com.typenull.pingdom.moderation.application.service.place.merge.AdminPlaceMergeService;
import com.typenull.pingdom.moderation.application.service.place.operating.AdminPlaceOperatingScheduleService;
import com.typenull.pingdom.moderation.application.service.place.quality.AdminPlaceQualityService;
import com.typenull.pingdom.moderation.application.service.place.recommendation.AdminPlaceRecommendationPolicyService;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/** 기능별 구현이 물리적으로 분리된 상태를 고정하는 구조 회귀 테스트다. */
class AdminPlaceServiceSplitTest {

    @org.junit.jupiter.api.Test
    void mapPlaceServiceRetainsOnlyDeletionUseCase() {
        assertThat(publicMethodNames(AdminMapPlaceService.class))
                .containsExactly("deletePlace");
    }

    @org.junit.jupiter.api.Test
    void mergeServiceOwnsMergeHistoryAndRestoreUseCases() {
        assertThat(publicMethodNames(AdminPlaceMergeService.class))
                .containsExactly("listMergeHistories", "mergePlaces", "restoreMerge");
    }

    @org.junit.jupiter.api.Test
    void operatingServiceOwnsOperatingScheduleUseCase() {
        assertThat(publicMethodNames(AdminPlaceOperatingScheduleService.class))
                .containsExactly("updatePlaceOperatingSchedule");
    }

    @org.junit.jupiter.api.Test
    void qualityServiceOwnsPlaceQualityUseCases() {
        assertThat(publicMethodNames(AdminPlaceQualityService.class))
                .containsExactly(
                        "createPlaceInformationEvidence",
                        "getPlaceInformationEvidence",
                        "reviewPlaceInformationEvidence",
                        "updatePlaceBasicInformation",
                        "updatePlaceCoordinates",
                        "updatePlaceDiscoveryStatus",
                        "updatePlaceGeocoding",
                        "updatePlaceKakaoPlaceId",
                        "updatePlaceOperatingStatus",
                        "updatePlaceTouristInfo");
    }

    @org.junit.jupiter.api.Test
    void recommendationServiceOwnsPolicyUseCases() {
        assertThat(publicMethodNames(AdminPlaceRecommendationPolicyService.class))
                .containsExactly("resyncRecommendationSnapshots", "updateRecommendationTraffic");
    }

    private List<String> publicMethodNames(Class<?> serviceType) {
        return Arrays.stream(serviceType.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .sorted()
                .toList();
    }
}
