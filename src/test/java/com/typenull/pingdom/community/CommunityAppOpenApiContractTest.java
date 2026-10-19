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
@SpringBootTest(properties={"pingdom.dev-profile.enabled=true", "pingdom.openapi.public-access.enabled=true"})
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
        assertThat(doc.at("/paths/~1community~1images/post/responses/413/description").asText())
                .contains("UPLOAD_TOO_LARGE");
    }

    @Test void appDocumentsOwnPostListAndPatchDeleteContracts() throws Exception {
        JsonNode doc = mapper.readTree(mvc.perform(get("/v3/api-docs/app")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(doc.at("/paths/~1users~1me~1community~1posts/get").isMissingNode()).isFalse();
        assertThat(doc.at("/paths/~1community~1posts~1{postId}/patch/requestBody/content/application~1json/schema/$ref").asText())
                .endsWith("CommunityPostUpdateRequest");
        for (String code : List.of("200", "400", "401", "404"))
            assertThat(doc.at("/paths/~1community~1posts~1{postId}/patch/responses").has(code)).as(code).isTrue();
        assertThat(doc.at("/paths/~1community~1posts~1{postId}/delete/responses").has("204")).isTrue();
        JsonNode update = doc.at("/components/schemas/CommunityPostUpdateRequest");
        assertThat(update.path("required").isMissingNode()).isTrue();
        assertThat(update.path("properties").has("changeRequested")).isFalse();
        assertThat(update.path("properties").has("countryCodeSpecified")).isFalse();
        assertThat(update.path("properties").path("imageIds").path("maxItems").asInt()).isEqualTo(10);
        assertThat(update.path("properties").path("countryCode").path("nullable").asBoolean()).isTrue();
        assertThat(update.path("properties").path("region").path("nullable").asBoolean()).isTrue();
        JsonNode own = doc.at("/components/schemas/MyCommunityPostSummary/properties");
        for (String field : List.of("postId", "title", "contentPreview", "imageUrl", "imageCount", "category",
                "createdAt", "updatedAt", "region", "viewCount", "likeCount", "commentCount", "hidden"))
            assertThat(own.has(field)).as(field).isTrue();
        assertThat(doc.at("/components/schemas/MyCommunityPostListResponse/properties").has("totalElements")).isTrue();
        assertThat(doc.at("/components/schemas/CommunityPostDetailResponse/properties").has("updatedAt")).isTrue();
    }
}
