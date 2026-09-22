package com.typenull.pingdom.shared.config.swagger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.shared.config.swagger.ApiAudience.Group;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

class SwaggerFunctionalGroupingTest {
    private final SpringdocGroupsConfig config = new SpringdocGroupsConfig();

    /**
     * 메서드 audience가 클래스 기본값을 덮어쓰고 태그만 있는 메서드는 미분류인지 확인.
     * admin 필터가 명시적으로 ADMIN인 메서드만 포함하는지도 검증.
     */
    @Test
    void resolvesExplicitMethodAudience() throws Exception {
        assertThat(Group.resolve(ExampleController.class.getMethod("defaultAudience"))).isEqualTo(Group.APP);
        assertThat(Group.resolve(ExampleController.class.getMethod("overrideAudience"))).isEqualTo(Group.ADMIN);
        assertThat(Group.resolve(UngroupedController.class.getMethod("operation"))).isNull();
        var filter = config.adminApi().getOpenApiMethodFilters().iterator().next();
        assertThat(filter.isMethodToInclude(ExampleController.class.getMethod("overrideAudience"))).isTrue();
        assertThat(filter.isMethodToInclude(ExampleController.class.getMethod("defaultAudience"))).isFalse();
        assertThat(filter.isMethodToInclude(UngroupedController.class.getMethod("operation"))).isFalse();
    }

    /**
     * 메서드 태그가 상속 태그 목록을 대체하면서 같은 Operation 객체와 operationId는 유지하는지 검증.
     */
    @Test
    void overridesMethodTagPreservingOperation() throws Exception {
        Operation operation = new Operation().operationId("stableId")
                .tags(List.of(SwaggerTagCatalog.ACCOUNT, SwaggerTagCatalog.TRAVEL));
        Operation result = config.functionalTagCustomizer().customize(operation,
                new HandlerMethod(new ExampleController(), "overrideTag"));
        assertThat(result).isSameAs(operation);
        assertThat(result.getOperationId()).isEqualTo("stableId");
        assertThat(result.getTags()).containsExactly(SwaggerTagCatalog.TRAVEL);
    }

    /**
     * 메서드 태그가 없으면 클래스 ACCOUNT 기능 태그가 적용되는지 검증.
     */
    @Test
    void inheritsClassDisplayTag() throws Exception {
        Operation result = config.functionalTagCustomizer().customize(new Operation(),
                new HandlerMethod(new ExampleController(), "defaultAudience"));
        assertThat(result.getTags()).containsExactly(SwaggerTagCatalog.ACCOUNT);
    }

    /**
     * 미분류 operation의 기존 태그는 유지하고 audience만 있고 기능 태그가 없는 메서드는 명시적 오류로 거절하는지 검증.
     */
    @Test
    void checksMissingGroupedFeatureTag() throws Exception {
        Operation operation = new Operation().tags(List.of("Voice AI"));
        assertThat(config.functionalTagCustomizer().customize(operation,
                new HandlerMethod(new UngroupedController(), "operation")).getTags()).containsExactly("Voice AI");
        assertThatThrownBy(() -> config.functionalTagCustomizer().customize(new Operation(),
                new HandlerMethod(new MissingTagController(), "operation")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("기능 분류가 없는 API");
    }

    /**
     * 사용된 관리자 기능 태그만 대시보드·예약·운영 순으로 배치하고 예약에 관리자용 설명을 적용하는지 검증.
     */
    @Test
    void ordersUsedGroupSections() {
        OpenAPI api = new OpenAPI().paths(new Paths()
                .addPathItem("/storage", new PathItem().get(new Operation().tags(List.of(SwaggerTagCatalog.OPERATIONS))))
                .addPathItem("/dashboard", new PathItem().get(new Operation().tags(List.of(SwaggerTagCatalog.DASHBOARD))))
                .addPathItem("/reservations", new PathItem().get(new Operation().tags(List.of(SwaggerTagCatalog.ADMIN_RESERVATION)))));
        config.functionalTagDescriptionsCustomizer().customise(api);
        config.adminApi().getOpenApiCustomizers().forEach(customizer -> customizer.customise(api));
        assertThat(api.getTags()).extracting(io.swagger.v3.oas.models.tags.Tag::getName)
                .containsExactly(SwaggerTagCatalog.DASHBOARD, SwaggerTagCatalog.ADMIN_RESERVATION, SwaggerTagCatalog.OPERATIONS);
        assertThat(api.getTags().get(1).getDescription()).isEqualTo("예약 조회 및 승인·거절");
    }

    /**
     * 기능 카탈로그에 속하지 않는 Voice AI 태그의 기존 설명이 유지되는지 검증.
     */
    @Test
    void ungroupedTagDescriptionIsPreserved() {
        OpenAPI api = new OpenAPI().paths(new Paths().addPathItem("/voice-ai/sessions",
                new PathItem().post(new Operation().tags(List.of("Voice AI")))))
                .tags(List.of(new io.swagger.v3.oas.models.tags.Tag().name("Voice AI").description("음성 AI")));
        config.functionalTagDescriptionsCustomizer().customise(api);
        assertThat(api.getTags()).hasSize(1);
        assertThat(api.getTags().getFirst().getDescription()).isEqualTo("음성 AI");
    }

    @ApiAudience(Group.APP)
    @Tag(name = SwaggerTagCatalog.ACCOUNT)
    static class ExampleController {
        /**
         * 클래스 APP audience와 ACCOUNT 태그를 상속하는 reflection 조회용 빈 메서드.
         */
        public void defaultAudience() { }

        /**
         * 클래스 APP 기본값을 메서드의 ADMIN audience로 덮어쓰는 reflection 입력.
         */
        @ApiAudience(Group.ADMIN)
        public void overrideAudience() { }

        /**
         * 클래스 ACCOUNT 태그를 메서드 TRAVEL 태그로 대체하는 HandlerMethod 입력.
         */
        @Tag(name = SwaggerTagCatalog.TRAVEL)
        public void overrideTag() { }
    }

    @Tag(name = "Admin")
    static class UngroupedController {
        /**
         * 표시 태그만 있고 ApiAudience가 없는 미분류 메서드를 재현.
         */
        public void operation() { }
    }

    @ApiAudience(Group.APP)
    static class MissingTagController {
        /**
         * ApiAudience는 있지만 기능 태그가 없는 잘못된 문서 구성을 재현.
         */
        public void operation() { }
    }
}
