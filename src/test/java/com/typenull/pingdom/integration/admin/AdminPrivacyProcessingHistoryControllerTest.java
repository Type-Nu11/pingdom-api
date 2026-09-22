package com.typenull.pingdom.integration.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingActorType;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingHistory;
import com.typenull.pingdom.privacy.infrastructure.persistence.PrivacyProcessingHistoryRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 개인정보 처리 이력의 기본 조회와 빈 페이지 응답을 저장 데이터로 검증한다.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminPrivacyProcessingHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PrivacyProcessingHistoryRepository privacyProcessingHistoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 개인정보 처리 이력과 사용자를 지워 이전 시나리오의 결과를 제거한다.
     */
    @BeforeEach
    void setUp() {
        privacyProcessingHistoryRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    /**
     * 선택 필터 없이 개인정보 처리 이력을 조회하고 삭제 후 빈 페이지의 건수와 페이지 메타데이터를 확인한다.
     */
    @Test
    void defaultAndEmptyHistoryPage() throws Exception {
        String adminAccessToken = createUserAndLogin("privacyHistoryAdmin", UserRole.ADMIN);
        privacyProcessingHistoryRepository.save(PrivacyProcessingHistory.builder()
                .subjectUserId(10L)
                .actorUserId(20L)
                .actorType(PrivacyProcessingActorType.ADMIN)
                .action(PrivacyProcessingAction.EXPORT_REQUESTED)
                .details("관리자 개인정보 내보내기")
                .requestId("privacy-history-request")
                .createdAt(LocalDateTime.of(2026, 8, 16, 12, 0))
                .build());

        mockMvc.perform(get("/admin/privacy-processing-histories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.histories.length()").value(1))
                .andExpect(jsonPath("$.histories[0].subjectUserId").value(10))
                .andExpect(jsonPath("$.histories[0].action").value(PrivacyProcessingAction.EXPORT_REQUESTED.name()))
                .andExpect(jsonPath("$.totalCount").value(1));

        privacyProcessingHistoryRepository.deleteAllInBatch();

        mockMvc.perform(get("/admin/privacy-processing-histories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.histories").isEmpty())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    /**
     * 지정 역할을 가진 사용자를 저장하고 실제 로그인 응답에서 접근 토큰을 가져온다.
     */
    private String createUserAndLogin(String username, UserRole role) throws Exception {
        userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(role)
                .build());

        LoginRequest loginRequest = new LoginRequest(username, "password123");
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
