package com.typenull.pingdom.shared.config.swagger;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class VoiceAiOpenApiConfigTest {
    @Test
    void publishesGeneralSearchAndPreservesExistingEnvelopeVariants() throws Exception {
        OpenAPI api = new OpenAPI().components(new Components()).paths(new Paths()
                .addPathItem("/voice-ai/sessions", new PathItem().post(operation()))
                .addPathItem("/voice-ai/sessions/{sessionId}/messages", new PathItem().post(operation()))
                .addPathItem("/voice-ai/sessions/{sessionId}/refresh", new PathItem().post(operation()))
                .addPathItem("/voice-ai/sessions/{sessionId}", new PathItem().delete(operation())));
        new VoiceAiOpenApiConfig().voiceAiContractCustomizer().customise(api);

        JsonNode published = Json.mapper().valueToTree(api);
        JsonNode union = published.at("/components/schemas/ProviderEnvelopeV1/oneOf");
        assertThat(union).hasSize(9);
        try (var input = getClass().getResourceAsStream("/openapi/provider-envelope.v1.schema.json")) {
            JsonNode source = new ObjectMapper().readTree(input);
            for (JsonNode original : source.path("oneOf")) {
                String kind = original.at("/properties/kind/const").asText();
                String command = original.at("/properties/command/const").asText();
                JsonNode converted = StreamSupport.stream(union.spliterator(), false)
                        .filter(node -> node.at("/properties/kind/enum/0").asText().equals(kind)
                                && node.at("/properties/command/enum/0").asText().equals(command))
                        .findFirst().orElseThrow();
                assertThat(converted.path("required")).containsExactlyInAnyOrderElementsOf(original.path("required"));
                assertThat(converted.path("additionalProperties").asBoolean(true)).isFalse();
                assertThat(converted.at("/properties/schemaVersion/enum/0").asInt()).isEqualTo(1);
                assertThat(converted.at("/properties/id/pattern"))
                        .isEqualTo(original.at("/properties/id/pattern"));
                if (kind.equals("command_request")) {
                    assertThat(converted.at("/properties/args/required"))
                            .containsExactlyInAnyOrderElementsOf(original.at("/properties/args/required"));
                    assertThat(converted.at("/properties/args/additionalProperties").asBoolean(true)).isFalse();
                }
            }
            JsonNode general = StreamSupport.stream(union.spliterator(), false)
                    .filter(node -> node.at("/properties/command/enum/0").asText().equals("searchNearbyPlaces"))
                    .findFirst().orElseThrow();
            assertThat(general.at("/properties/args/properties")).hasSize(2);
            assertThat(general.at("/properties/args/properties/useCurrentLocation/type").asText()).isEqualTo("boolean");
            assertThat(general.at("/properties/args/properties/touristCategory/enum"))
                    .isEqualTo(source.at("/oneOf/0/properties/args/properties/touristCategory/enum"));
        }
        assertThat(published.at("/paths/~1voice-ai~1sessions~1{sessionId}~1messages/post/responses/200/content/application~1json/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/ProviderEnvelopeV1");
    }

    private Operation operation() {
        return new Operation().responses(new ApiResponses());
    }
}
