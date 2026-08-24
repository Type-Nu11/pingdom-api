package com.typenull.pingdom.integration.swagger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest(properties = "pingdom.dev-profile.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SwaggerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homeApiIsAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Pingdom Backend is running."));
    }

    @Test
    void swaggerUiPathIsAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/swagger-ui/index.html"));
    }

    @Test
    void swaggerUiIndexIsAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void apiDocsAreAccessibleWithoutAuthentication() throws Exception {
        for (String apiDocsPath : new String[]{
                "/v3/api-docs",
                "/v3/api-docs/app",
                "/v3/api-docs/common",
                "/v3/api-docs/consulting",
                "/v3/api-docs/admin",
                "/v3/api-docs/merchant"
        }) {
            mockMvc.perform(get(apiDocsPath))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void appGroupDocsContainPlaceQueryApis() throws Exception {
        mockMvc.perform(get("/v3/api-docs/app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/places']").exists())
                .andExpect(jsonPath("$.paths['/places/{id}']").exists())
                .andExpect(jsonPath("$.paths['/places/{placeId}/card']").exists())
                .andExpect(jsonPath("$.paths['/place']").doesNotExist())
                .andExpect(jsonPath("$.paths['/place/{id}']").doesNotExist())
                .andExpect(jsonPath("$.paths['/users/bookmarks']").doesNotExist())
                .andExpect(jsonPath("$.paths['/map/bookmarks']").exists())
                .andExpect(jsonPath("$.paths['/places/coordinates']").doesNotExist())
                .andExpect(jsonPath("$.paths['/places/upload']").doesNotExist())
                .andExpect(jsonPath("$.paths['/places/{id}']").exists())
                .andExpect(jsonPath("$.paths['/map/places/coordinates']").doesNotExist());
    }

    @Test
    void appGroupDocsIncludePreviouslyUnmatchedAppApis() throws Exception {
        mockMvc.perform(get("/v3/api-docs/app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/popup-campaigns']").exists())
                .andExpect(jsonPath("$.paths['/payments']").exists())
                .andExpect(jsonPath("$.paths['/reservations']").exists());
    }

    @Test
    void locationAnalysisApiDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs/app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/analysis/reports/location'].post").exists())
                .andExpect(jsonPath("$.paths['/analysis/reports/location'].post.security").doesNotExist());
    }

    @Test
    void consultingGroupDocsContainOnlyConsultationIntroApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs/consulting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/consultations/intro'].post").exists());

        mockMvc.perform(get("/v3/api-docs/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/consultations/intro']").doesNotExist());
    }

    @Test
    void appGroupDocsContainVisitorVerificationReportApis() throws Exception {
        mockMvc.perform(get("/v3/api-docs/app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/visitor-verification-reports'].get").exists())
                .andExpect(jsonPath("$.paths['/visitor-verification-reports'].post").exists())
                .andExpect(jsonPath("$.paths['/visitor-verification-reports/{reportId}'].get").exists())
                .andExpect(jsonPath("$.paths['/visitor-verification-reports/{reportId}/corrections'].get").exists())
                .andExpect(jsonPath("$.paths['/visitor-verification-reports/{reportId}/corrections'].post").exists())
                .andExpect(jsonPath("$.paths['/admin/visitor-verification-reports']").doesNotExist());
    }

    @Test
    void appGroupDocsContainScoutFieldReportApis() throws Exception {
        mockMvc.perform(get("/v3/api-docs/app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/scout-field-reports'].get").exists())
                .andExpect(jsonPath("$.paths['/scout-field-reports'].post").exists())
                .andExpect(jsonPath("$.paths['/scout-field-reports/{reportId}'].get").exists())
                .andExpect(jsonPath("$.paths['/admin/scout-field-reports']").doesNotExist());
    }

    @Test
    void webGroupDocsContainVisitorVerificationCorrectionApis() throws Exception {
        mockMvc.perform(get("/v3/api-docs/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/admin/visitor-verification-reports'].get").exists())
                .andExpect(jsonPath(
                        "$.paths['/admin/visitor-verification-reports'].get.security[0].bearerAuth"
                ).isArray())
                .andExpect(jsonPath(
                        "$.paths['/admin/visitor-verification-reports/{reportId}/review'].post.security[0].bearerAuth"
                ).isArray())
                .andExpect(jsonPath("$.paths['/admin/visitor-verification-reports/corrections'].get").exists())
                .andExpect(jsonPath(
                        "$.paths['/admin/visitor-verification-reports/corrections'].get.security[0].bearerAuth"
                ).isArray())
                .andExpect(jsonPath(
                        "$.paths['/admin/visitor-verification-reports/corrections/{correctionId}/review'].post"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/admin/visitor-verification-reports/corrections/{correctionId}/review']"
                                + ".post.security[0].bearerAuth"
                ).isArray())
                .andExpect(jsonPath("$.paths['/visitor-verification-reports/{reportId}/corrections']").doesNotExist());
    }

    @Test
    void webGroupDocsContainScoutFieldReportReviewApis() throws Exception {
        mockMvc.perform(get("/v3/api-docs/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/admin/scout-field-reports'].get").exists())
                .andExpect(jsonPath(
                        "$.paths['/admin/scout-field-reports'].get.security[0].bearerAuth"
                ).isArray())
                .andExpect(jsonPath("$.paths['/admin/scout-field-reports/{reportId}/review'].post").exists())
                .andExpect(jsonPath(
                        "$.paths['/admin/scout-field-reports/{reportId}/review'].post.security[0].bearerAuth"
                ).isArray())
                .andExpect(jsonPath("$.paths['/scout-field-reports']").doesNotExist());
    }
}
