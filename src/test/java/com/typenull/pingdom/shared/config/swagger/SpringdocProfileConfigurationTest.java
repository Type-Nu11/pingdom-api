package com.typenull.pingdom.shared.config.swagger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Profile;

class SpringdocProfileConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    SpringdocSecurityConfig.class,
                    PlaceExplorationOpenApiConfig.class,
                    SpringdocGroupsConfig.class
            );

    /**
     * OpenAPI 그룹·장소 탐색 설정에 Profile 제한이 없어 특정 환경에서 문서 구성이 누락되지 않는지 검증.
     */
    @Test
    void allowsGroupsAcrossProfiles() {
        assertThat(SpringdocGroupsConfig.class.getAnnotation(Profile.class)).isNull();
        assertThat(PlaceExplorationOpenApiConfig.class.getAnnotation(Profile.class)).isNull();
    }

    /**
     * Bearer 보안 문서 설정에 Profile 제한이 없는지 검증.
     */
    @Test
    void allowsBearerConfigAcrossProfiles() {
        assertThat(SpringdocSecurityConfig.class.getAnnotation(Profile.class)).isNull();
    }

    /**
     * 기본 컨텍스트에 app·common·consulting·admin·merchant 그룹 빈이 모두 등록되는지 검증.
     */
    @Test
    void registersDefaultOpenApiGroups() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("appApi");
            assertThat(context).hasBean("commonApi");
            assertThat(context).hasBean("consultingApi");
            assertThat(context).hasBean("adminApi");
            assertThat(context).hasBean("merchantApi");
        });
    }
}
