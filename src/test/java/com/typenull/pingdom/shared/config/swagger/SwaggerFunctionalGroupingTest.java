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

    @Test
    void methodAudienceOverridesClassWithoutUsingDisplayTags() throws Exception {
        assertThat(Group.resolve(ExampleController.class.getMethod("defaultAudience"))).isEqualTo(Group.APP);
        assertThat(Group.resolve(ExampleController.class.getMethod("overrideAudience"))).isEqualTo(Group.ADMIN);
        assertThat(Group.resolve(UngroupedController.class.getMethod("operation"))).isNull();
        var filter = config.adminApi().getOpenApiMethodFilters().iterator().next();
        assertThat(filter.isMethodToInclude(ExampleController.class.getMethod("overrideAudience"))).isTrue();
        assertThat(filter.isMethodToInclude(ExampleController.class.getMethod("defaultAudience"))).isFalse();
        assertThat(filter.isMethodToInclude(UngroupedController.class.getMethod("operation"))).isFalse();
    }

    @Test
    void methodDisplayTagReplacesInheritedTagsWithoutChangingOperationContract() throws Exception {
        Operation operation = new Operation().operationId("stableId")
                .tags(List.of(SwaggerTagCatalog.ACCOUNT, SwaggerTagCatalog.TRAVEL));
        Operation result = config.functionalTagCustomizer().customize(operation,
                new HandlerMethod(new ExampleController(), "overrideTag"));
        assertThat(result).isSameAs(operation);
        assertThat(result.getOperationId()).isEqualTo("stableId");
        assertThat(result.getTags()).containsExactly(SwaggerTagCatalog.TRAVEL);
    }

    @Test
    void classDisplayTagIsUsedWhenMethodHasNoTag() throws Exception {
        Operation result = config.functionalTagCustomizer().customize(new Operation(),
                new HandlerMethod(new ExampleController(), "defaultAudience"));
        assertThat(result.getTags()).containsExactly(SwaggerTagCatalog.ACCOUNT);
    }

    @Test
    void ungroupedOperationIsPreservedAndMissingGroupedTagFailsExplicitly() throws Exception {
        Operation operation = new Operation().tags(List.of("Voice AI"));
        assertThat(config.functionalTagCustomizer().customize(operation,
                new HandlerMethod(new UngroupedController(), "operation")).getTags()).containsExactly("Voice AI");
        assertThatThrownBy(() -> config.functionalTagCustomizer().customize(new Operation(),
                new HandlerMethod(new MissingTagController(), "operation")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("기능 분류가 없는 API");
    }

    @Test
    void documentListsOnlyUsedSectionsInWorkflowOrderWithGroupSpecificDescriptions() {
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
        public void defaultAudience() { }

        @ApiAudience(Group.ADMIN)
        public void overrideAudience() { }

        @Tag(name = SwaggerTagCatalog.TRAVEL)
        public void overrideTag() { }
    }

    @Tag(name = "Admin")
    static class UngroupedController {
        public void operation() { }
    }

    @ApiAudience(Group.APP)
    static class MissingTagController {
        public void operation() { }
    }
}
