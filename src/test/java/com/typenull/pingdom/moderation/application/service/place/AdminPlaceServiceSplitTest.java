package com.typenull.pingdom.moderation.application.service.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/** 기능별 구현이 물리적으로 분리된 상태를 고정하는 구조 회귀 테스트다. */
class AdminPlaceServiceSplitTest {

    private List<String> publicMethodNames(Class<?> serviceType) {
        return Arrays.stream(serviceType.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .sorted()
                .toList();
    }
}
