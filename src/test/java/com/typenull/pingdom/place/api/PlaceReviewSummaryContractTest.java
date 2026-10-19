package com.typenull.pingdom.place.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.place.api.dto.review.PlaceReviewSummaryResponse;
import com.typenull.pingdom.shared.config.swagger.ApiAudience;
import com.typenull.pingdom.shared.config.swagger.SpringdocGroupsConfig;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;
import com.typenull.pingdom.shared.security.annotation.AuthenticatedOnly;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

class PlaceReviewSummaryContractTest {

    @Test
    void usesExistingCommonGroupReviewTagAndAuthenticatedContract() throws Exception {
        var controller = PlaceReviewSummaryController.class;
        var method = controller.getMethod("summary", Long.class);
        var grouping = new SpringdocGroupsConfig();

        assertThat(ApiAudience.Group.resolve(method)).isEqualTo(ApiAudience.Group.COMMON);
        assertThat(grouping.commonApi().getOpenApiMethodFilters()).allMatch(filter -> filter.isMethodToInclude(method));
        assertThat(grouping.appApi(api -> {}).getOpenApiMethodFilters()).noneMatch(filter -> filter.isMethodToInclude(method));
        assertThat(controller.getAnnotation(Tag.class).name()).isEqualTo(SwaggerTagCatalog.PLACE_REVIEW);
        assertThat(controller.isAnnotationPresent(AuthenticatedOnly.class)).isTrue();
        assertThat(controller.getAnnotation(SecurityRequirement.class).name()).isEqualTo("bearerAuth");
        assertThat(method.getAnnotation(ApiResponses.class).value()).extracting(response -> response.responseCode())
                .containsExactly("200", "400", "401", "404", "500");
    }

    @Test
    void declaresRequiredInt64FieldsAndReasonEnumInGeneratedSchema() {
        var schemas = ModelConverters.getInstance().readAll(PlaceReviewSummaryResponse.class);
        Schema<?> summary = schemas.get("PlaceReviewSummaryResponse");
        Schema<?> reasonCount = schemas.get("ReasonCount");
        Schema<?> reason = reasonCount.getProperties().get("reason");

        assertThat(summary.getRequired()).containsExactlyInAnyOrder("placeId", "totalReviewCount", "participantCount", "reasonCounts");
        assertThat(summary.getProperties().get("totalReviewCount").getFormat()).isEqualTo("int64");
        assertThat(summary.getProperties().get("participantCount").getFormat()).isEqualTo("int64");
        assertThat(reasonCount.getRequired()).containsExactlyInAnyOrder("reason", "count");
        assertThat(reason.getEnum()).extracting(Object::toString).containsExactly(
                "FRIENDLY", "EASY_TO_FIND", "GOOD_FOOD", "MULTILINGUAL_SUPPORT", "PARKING", "PHOTO_SPOT", "CLEAN");
        assertThat(reasonCount.getProperties().get("count").getFormat()).isEqualTo("int64");
    }
}
