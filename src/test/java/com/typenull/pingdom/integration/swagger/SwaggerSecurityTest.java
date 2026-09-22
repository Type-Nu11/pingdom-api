package com.typenull.pingdom.integration.swagger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * dev 프로필의 Swagger 공개 접근과 그룹별 경로·인증 문서 계약을 검증.
 */
@Tag("integration")
@SpringBootTest(properties = "pingdom.dev-profile.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SwaggerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 미인증 루트 요청이 200과 서버 실행 메시지를 반환하는지 확인.
     */
    @Test
    void publicHome() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Pingdom Backend is running."));
    }

    /**
     * dev 프로필의 Swagger index가 인증 없이 200인지 확인.
     */
    @Test
    void publicSwaggerIndex() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    /**
     * dev 프로필에서 통합·app·common·consulting·admin·merchant 문서가 모두 미인증 조회 가능한지 확인.
     */
    @Test
    void publicApiDocs() throws Exception {
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

    /**
     * Swagger 설정의 그룹 이름과 URL에 app·common·consulting·admin·merchant가 순서와 무관하게 포함되는지 확인.
     */
    @Test
    void swaggerGroups() throws Exception {
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urls[*].name").value(containsInAnyOrder(
                        "app", "common", "consulting", "admin", "merchant"
                )))
                .andExpect(jsonPath("$.urls[*].url").value(containsInAnyOrder(
                        "/v3/api-docs/app",
                        "/v3/api-docs/common",
                        "/v3/api-docs/consulting",
                        "/v3/api-docs/admin",
                        "/v3/api-docs/merchant"
                )));
    }

    /**
     * app 문서에 현재 장소 목록·상세·카드가 있고 구형 단수·지도·업로드 경로는 없는지 확인.
     */
    @Test
    void appPlacePaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs/app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/places']").exists())
                .andExpect(jsonPath("$.paths['/places/{id}']").exists())
                .andExpect(jsonPath("$.paths['/places/{placeId}/card']").exists())
                .andExpect(jsonPath("$.paths['/place']").doesNotExist())
                .andExpect(jsonPath("$.paths['/place/{id}']").doesNotExist())
                .andExpect(jsonPath("$.paths['/users/bookmarks']").doesNotExist())
                .andExpect(jsonPath("$.paths['/map/bookmarks']").doesNotExist())
                .andExpect(jsonPath("$.paths['/places/coordinates']").doesNotExist())
                .andExpect(jsonPath("$.paths['/places/upload']").doesNotExist())
                .andExpect(jsonPath("$.paths['/places/{id}']").exists())
                .andExpect(jsonPath("$.paths['/map/places/coordinates']").doesNotExist());
    }

    /**
     * 팝업·결제·예약 경로가 app 그룹 문서에서 누락되지 않는지 확인.
     */
    @Test
    void appCommercePaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs/app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/popup-campaigns']").exists())
                .andExpect(jsonPath("$.paths['/payments']").exists())
                .andExpect(jsonPath("$.paths['/reservations']").exists());
    }

    /**
     * 입지 분석 문서에 Bearer 인증과 401·403 응답이 선언됐는지 확인. 실제 권한 거절 요청은 검증 범위에서 제외.
     */
    @Test
    void analysisSecurityContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs/app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/analysis/reports/location'].post").exists())
                .andExpect(jsonPath(
                        "$.paths['/analysis/reports/location'].post.security[0].bearerAuth"
                ).isArray())
                .andExpect(jsonPath(
                        "$.paths['/analysis/reports/location'].post.responses['401']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/analysis/reports/location'].post.responses['403']"
                ).exists());
    }

    /**
     * 상담 intro가 consulting에 포함되고 admin에는 없는지 확인. consulting 전체 경로 개수는 검증 범위에서 제외.
     */
    @Test
    void consultingIntroGrouping() throws Exception {
        mockMvc.perform(get("/v3/api-docs/consulting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/consultations/intro'].post").exists());

        mockMvc.perform(get("/v3/api-docs/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/consultations/intro']").doesNotExist());
    }

    /**
     * app 문서에 방문 검증 제보·수정 요청이 포함되고 관리자 경로는 제외되는지 확인.
     */
    @Test
    void appVerificationPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs/app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/visitor-verification-reports'].get").exists())
                .andExpect(jsonPath("$.paths['/visitor-verification-reports'].post").exists())
                .andExpect(jsonPath("$.paths['/visitor-verification-reports/{reportId}'].get").exists())
                .andExpect(jsonPath("$.paths['/visitor-verification-reports/{reportId}/corrections'].get").exists())
                .andExpect(jsonPath("$.paths['/visitor-verification-reports/{reportId}/corrections'].post").exists())
                .andExpect(jsonPath("$.paths['/admin/visitor-verification-reports']").doesNotExist());
    }

    /**
     * app 문서에 scout 제보 목록·생성·상세가 있고 관리자 목록은 없는지 확인.
     */
    @Test
    void appScoutPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs/app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/scout-field-reports'].get").exists())
                .andExpect(jsonPath("$.paths['/scout-field-reports'].post").exists())
                .andExpect(jsonPath("$.paths['/scout-field-reports/{reportId}'].get").exists())
                .andExpect(jsonPath("$.paths['/admin/scout-field-reports']").doesNotExist());
    }

    /**
     * admin 문서의 방문 검증·수정 검토 경로에 인증 요구가 있고 사용자 수정 요청 경로는 제외되는지 확인.
     */
    @Test
    void adminVerificationPaths() throws Exception {
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

    /**
     * admin 문서에 scout 목록·검토의 인증 계약이 있고 사용자 제보 경로는 없는지 확인.
     */
    @Test
    void adminScoutPaths() throws Exception {
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
