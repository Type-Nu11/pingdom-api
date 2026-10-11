package com.typenull.pingdom.community;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest(properties="pingdom.dev-profile.enabled=true")
@AutoConfigureMockMvc @ActiveProfiles("dev")
class CommunityAppOpenApiContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Test void appDocumentsAdditiveImageReplyAndFilterContracts() throws Exception {
        JsonNode doc=mapper.readTree(mvc.perform(get("/v3/api-docs/app")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for(String path:List.of("/community/posts","/community/countries","/community/images",
                "/community/images/{imageId}","/community/comments/{commentId}/likes")) {
            assertThat(doc.path("paths").has(path)).as(path).isTrue();
        }
        JsonNode list=doc.at("/components/schemas/CommunityPostSummary/properties");
        for(String field:List.of("postId","title","contentPreview","imageUrl","imageCount","countryCode","region",
                "createdAt","viewCount","likeCount","commentCount","category")) assertThat(list.has(field)).as(field).isTrue();
        JsonNode request=doc.at("/components/schemas/CommunityPostCreateRequest/properties");
        assertThat(request.path("imageIds").path("maxItems").asInt()).isEqualTo(10);
        assertThat(request.has("countryCode")).isTrue();
        assertThat(doc.at("/components/schemas/CommunityCommentSummary/properties").has("parentCommentId")).isTrue();
        assertThat(doc.at("/components/schemas/CommunityPostDetailResponse/properties/images/items/$ref").asText())
                .endsWith("CommunityPostImageItem");
        assertThat(doc.at("/paths/~1community~1images/post/requestBody/content").has("multipart/form-data")).isTrue();
    }
}
