package com.typenull.pingdom.moderation.application.service.place;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.moderation.application.service.place.quality.AdminMapPlaceService;
import com.typenull.pingdom.moderation.application.service.place.merge.AdminPlaceMergeService;
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

    private List<String> publicMethodNames(Class<?> serviceType) {
        return Arrays.stream(serviceType.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .sorted()
                .toList();
    }
}
