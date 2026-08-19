package com.typenull.pingdom.integration.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.api.dto.ad.AdminAdCreateRequest;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditLog;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.ad.AdminAd;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAdRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.LocalDateTime;

@SpringBootTest(properties = {
        "spring.cloud.aws.s3.bucket=test-bucket",
        "spring.cloud.aws.region.static=ap-northeast-2",
        "spring.cloud.aws.credentials.access-key=test-access-key",
        "spring.cloud.aws.credentials.secret-key=test-secret-key",
        "spring.security.oauth2.client.registration.google.client-id=test-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-client-secret",
        "fcm.enabled=false",
        "fcm.key-path=dummy"
})
@AutoConfigureMockMvc
@Transactional
class AdminAdControllerTest {

    @MockBean
    private S3Client s3Client;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminAdRepository adminAdRepository;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @BeforeEach
    void setUp() {
        adminAuditLogRepository.deleteAllInBatch();
        adminAdRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void createAdReturnsCreated() throws Exception {
        String adminAccessToken = createAdminAndLogin();

        AdminAdCreateRequest request = new AdminAdCreateRequest(
                "여름 한정 출석 이벤트",
                "https://cdn.pingdom.com/banner/summer-event.png",
                "https://pingdom.com/events/summer",
                LocalDateTime.of(2026, 6, 20, 9, 0),
                LocalDateTime.of(2026, 6, 30, 23, 59, 59)
        );

        mockMvc.perform(post("/admin/ad")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adId").isNumber())
                .andExpect(jsonPath("$.title").value("여름 한정 출석 이벤트"))
                .andExpect(jsonPath("$.message").value("이벤트/광고를 등록했습니다."));

        assertEquals(1, adminAuditLogRepository.findAll().size());
        AdminAuditLog auditLog = adminAuditLogRepository.findAll().getFirst();
        assertEquals(AdminAuditAction.AD_CREATED, auditLog.getAction());
        assertEquals(AdminAuditTargetType.AD, auditLog.getTargetType());
    }

    @Test
    void createAdReturnsBadRequestWhenPeriodIsInvalid() throws Exception {
        String adminAccessToken = createAdminAndLogin();

        AdminAdCreateRequest request = new AdminAdCreateRequest(
                "잘못된 이벤트",
                "https://cdn.pingdom.com/banner/invalid.png",
                "https://pingdom.com/events/invalid",
                LocalDateTime.of(2026, 6, 30, 23, 59, 59),
                LocalDateTime.of(2026, 6, 20, 9, 0)
        );

        mockMvc.perform(post("/admin/ad")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AD_INVALID_PERIOD"));
    }

    @Test
    void listAdsReturnsEmptyPageWithoutOptionalFilters() throws Exception {
        String adminAccessToken = createAdminAndLogin();

        mockMvc.perform(get("/admin/ad")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ads").isEmpty())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void deleteAdReturnsNoContent() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        AdminAd savedAd = adminAdRepository.save(AdminAd.builder()
                .title("삭제 대상 광고")
                .imageUrl("https://cdn.pingdom.com/banner/delete-target.png")
                .redirectUrl("https://pingdom.com/events/delete-target")
                .startAt(LocalDateTime.of(2026, 6, 20, 9, 0))
                .endAt(LocalDateTime.of(2026, 6, 30, 23, 59, 59))
                .createdAt(LocalDateTime.of(2026, 6, 18, 12, 0))
                .build());

        mockMvc.perform(delete("/admin/ad/{adId}", savedAd.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isNoContent());

        assertEquals(1, adminAuditLogRepository.findAll().size());
        AdminAuditLog auditLog = adminAuditLogRepository.findAll().getFirst();
        assertEquals(AdminAuditAction.AD_DELETED, auditLog.getAction());
        assertEquals(String.valueOf(savedAd.getId()), auditLog.getTargetId());
    }

    @Test
    void deleteAdReturnsNotFoundWhenAdDoesNotExist() throws Exception {
        String adminAccessToken = createAdminAndLogin();

        mockMvc.perform(delete("/admin/ad/{adId}", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AD_NOT_FOUND"));
    }

    private String createAdminAndLogin() throws Exception {
        userRepository.save(User.builder()
                .username("adminAdTester")
                .email("admin-ad@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.ADMIN)
                .build());

        LoginRequest loginRequest = new LoginRequest("adminAdTester", "password123");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .textValue();
    }
}
