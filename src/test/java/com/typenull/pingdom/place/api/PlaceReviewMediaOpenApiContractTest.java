package com.typenull.pingdom.place.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest(properties = "pingdom.dev-profile.enabled=true")
@AutoConfigureMockMvc
class PlaceReviewMediaOpenApiContractTest {

    private static final String MEDIA_PATH = "/places/{placeId}/reviews/media";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exposesMultipartReviewMediaUploadInTheCommonOpenApiDocument() throws Exception {
        JsonNode commonDocument = readApiDocs("/v3/api-docs/common");
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode upload = commonDocument.at("/paths/~1places~1{placeId}~1reviews~1media/post");

        assertThat(commonDocument.path("paths").has(MEDIA_PATH)).isTrue();
        assertThat(appDocument.path("paths").has(MEDIA_PATH)).isFalse();
        assertThat(upload.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(upload.at("/requestBody/content/multipart~1form-data/schema/properties/file/type").asText())
                .isEqualTo("string");
        assertThat(upload.at("/requestBody/content/multipart~1form-data/schema/properties/file/format").asText())
                .isEqualTo("binary");
        assertThat(upload.path("responses").has("201")).isTrue();
        assertThat(upload.path("responses").has("413")).isTrue();
        assertThat(upload.path("responses").has("415")).isTrue();
        assertThat(upload.path("responses").has("503")).isTrue();
    }

    private JsonNode readApiDocs(String path) throws Exception {
        String body = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }
}
