package com.typenull.pingdom.integration.swagger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.DisplayStatus;
import com.typenull.pingdom.moderation.application.query.place.map.AdminPlaceMapProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest(properties = "pingdom.openapi.public-access.enabled=true")
@AutoConfigureMockMvc
class AdminPlaceMapOpenApiContractTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private AdminPlaceMapProperties properties;

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs/admin", "/v3/api-docs"})
    void publishesCountPolicyAndServerControlledLimit(String path) throws Exception {
        var doc = document(path);
        var operation = doc.path("paths").path("/admin/places/map").path("get");
        assertThat(operation.path("description").asText()).contains("v2", "displayLimit", "ZOOM_REQUIRED", "no-store");
        assertThat(operation.at("/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/AdminPlaceMapResponse");
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(operation.path("responses").has("503")).isTrue();
        var parameters = new ArrayList<String>();
        operation.path("parameters").forEach(p -> parameters.add(p.path("name").asText()));
        assertThat(parameters).contains("zoom", "width", "height", "keyword", "category", "discoveryStatus", "operatingStatus")
                .doesNotContain("page", "limit", "sortParam", "displayLimit");
        var schema = doc.at("/components/schemas/AdminPlaceMapResponse/properties");
        assertThat(schema.at("/displayLimit/type").asText()).isEqualTo("integer");
        assertThat(schema.at("/displayLimit/maximum").asInt()).isEqualTo(100);
        var statuses = new ArrayList<String>();
        schema.at("/displayStatus/enum").forEach(s -> statuses.add(s.asText()));
        assertThat(statuses).containsExactly("MARKERS", "ZOOM_REQUIRED");
        assertThat(schema.at("/regions/deprecated").asBoolean()).isTrue();
        assertThat(schema.at("/clusters/deprecated").asBoolean()).isTrue();
        assertThat(properties.displayLimit()).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs/admin", "/v3/api-docs"})
    void examplesAgreeWithMarkersEmptyAndExcessStates(String path) throws Exception {
        var examples = document(path).path("paths").path("/admin/places/map").path("get")
                .at("/responses/200/content/application~1json/examples");
        assertThat(examples.size()).isEqualTo(3);
        for (String name : List.of("markers", "empty", "zoomRequired")) {
            var result = mapper.treeToValue(examples.path(name).path("value"), AdminPlaceMapResponse.class);
            assertThat(result.displayLimit()).isEqualTo(100);
            assertThat(result.regions()).isEmpty();
            assertThat(result.clusters()).isEmpty();
            if (result.displayStatus() == DisplayStatus.MARKERS) {
                assertThat(result.viewportPlaceCount()).isEqualTo(result.markers().size());
                assertThat(result.complete()).isTrue();
                assertThat(result.incompleteReason()).isNull();
                assertThat(result.recoveryAction()).isNull();
            } else {
                assertThat(result.displayStatus()).isEqualTo(DisplayStatus.ZOOM_REQUIRED);
                assertThat(result.viewportPlaceCount()).isEqualTo(101);
                assertThat(result.markers()).isEmpty();
                assertThat(result.complete()).isFalse();
                assertThat(result.incompleteReason()).isEqualTo("RESULT_BUDGET_EXCEEDED");
                assertThat(result.recoveryAction()).isEqualTo("ZOOM_IN");
            }
        }
    }

    @Test
    void keepsLegacyGroupRouteAndSeparatesPublicMapContract() throws Exception {
        var admin = document("/v3/api-docs/admin");
        assertThat(admin.path("paths").path("/admin/places/map/clusters/{clusterId}/places").path("get")
                .path("description").asText()).contains("호환", "v2 지도 흐름에서는 호출하지 않습니다");
        var clusterSchema = admin.at("/components/schemas/AdminPlaceMapCluster");
        assertThat(clusterSchema.path("required")).extracting(JsonNode::asText).contains("representative");
        assertThat(clusterSchema.at("/properties/representative/$ref").asText())
                .isEqualTo("#/components/schemas/AdminPlaceMapMarker");
        var app = document("/v3/api-docs/app");
        assertThat(app.path("paths").has("/admin/places/map")).isFalse();
    }

    private JsonNode document(String path) throws Exception {
        return mapper.readTree(mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }
}
