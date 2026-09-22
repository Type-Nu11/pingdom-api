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

/** 기능별 구현이 물리적으로 분리된 상태를 고정하는 구조 회귀 테스트. */
class AdminPlaceServiceSplitTest {

    /**
     * 관리자 장소 서비스에 선언된 공개 메서드가 deletePlace뿐인지 확인해 삭제 책임의 분리를 고정.
     */
    @org.junit.jupiter.api.Test
    void limitsMapPlaceServiceToDeletion() {
        assertThat(publicMethodNames(AdminMapPlaceService.class))
                .containsExactly("deletePlace");
    }

    /**
     * 병합 서비스의 공개 API가 이력 조회·병합·복원 세 메서드로 제한되는지 구조 회귀를 검증.
     */
    @org.junit.jupiter.api.Test
    void isolatesPlaceMergeOperations() {
        assertThat(publicMethodNames(AdminPlaceMergeService.class))
                .containsExactly("listMergeHistories", "mergePlaces", "restoreMerge");
    }

    /**
     * 운영 일정 서비스가 운영 일정 수정 메서드만 공개하는지 검증.
     */
    @org.junit.jupiter.api.Test
    void isolatesOperatingScheduleUpdate() {
        assertThat(publicMethodNames(AdminPlaceOperatingScheduleService.class))
                .containsExactly("updatePlaceOperatingSchedule");
    }

    /**
     * 장소 품질 서비스의 공개 메서드 목록이 근거·기본 정보·좌표·공개 및 영업 상태·관광 정보 관리로 고정되는지 검증.
     */
    @org.junit.jupiter.api.Test
    void isolatesPlaceQualityOperations() {
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

    /**
     * 추천 정책 서비스가 추천 스냅샷 재동기화와 트래픽 수정만 공개하는지 검증.
     */
    @org.junit.jupiter.api.Test
    void isolatesRecommendationPolicyOperations() {
        assertThat(publicMethodNames(AdminPlaceRecommendationPolicyService.class))
                .containsExactly("resyncRecommendationSnapshots", "updateRecommendationTraffic");
    }

    /**
     * 선언된 공개 메서드 이름만 추출해 정렬함으로써 reflection 반환 순서에 영향받지 않는 API 집합 비교를 지원.
     */
    private List<String> publicMethodNames(Class<?> serviceType) {
        return Arrays.stream(serviceType.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .sorted()
                .toList();
    }
}
