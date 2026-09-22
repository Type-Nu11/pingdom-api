package com.typenull.pingdom.notification;

import com.typenull.pingdom.notification.api.dto.settings.NotificationSettingUpdateRequest;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationSettingRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class NotificationSettingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * 알림 설정과 사용자를 제거해 기본값 조회 및 설정 변경을 독립적으로 검증.
     */
    @BeforeEach
    void setUp() {
        notificationSettingRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    /**
     * 설정이 없는 사용자의 조회는 신규 핫플·좋아요 허용, 방해 금지 해제, 서울 시간대 기본값을 반환하면서 DB 행을 만들지 않는지 검증.
     */
    @Test
    void returnsDefaultsWithoutPersistingSettings() throws Exception {
        User user = saveUser("settinguser");

        mockMvc.perform(get("/notifications/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newHotplaceEnabled").value(true))
                .andExpect(jsonPath("$.newLikeEnabled").value(true))
                .andExpect(jsonPath("$.quietHoursEnabled").value(false))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"));

        org.junit.jupiter.api.Assertions.assertTrue(
                notificationSettingRepository.findByUserId(user.getId()).isEmpty()
        );
    }

    /**
     * 시작·종료 시각 없이 방해 금지를 켜면 400과 INVALID_QUIET_HOURS를 반환하는지 검증.
     */
    @Test
    void rejectsQuietHoursWithoutTimes() throws Exception {
        User user = saveUser("invalidquiet");
        NotificationSettingUpdateRequest request = new NotificationSettingUpdateRequest(
                null,
                null,
                true,
                null,
                null,
                null
        );

        mockMvc.perform(patch("/notifications/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUIET_HOURS"));
    }

    /**
     * 일부 알림 설정과 UTC 방해 금지 시간을 수정하면 지정한 값이 반영되고 생략한 핫플 알림 기본값은 유지되는지 검증.
     */
    @Test
    void updatesNotificationPreferences() throws Exception {
        User user = saveUser("updatesetting");
        NotificationSettingUpdateRequest request = new NotificationSettingUpdateRequest(
                null,
                false,
                true,
                java.time.LocalTime.of(22, 0),
                java.time.LocalTime.of(8, 0),
                "UTC"
        );

        mockMvc.perform(patch("/notifications/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newHotplaceEnabled").value(true))
                .andExpect(jsonPath("$.newLikeEnabled").value(false))
                .andExpect(jsonPath("$.quietHoursEnabled").value(true))
                .andExpect(jsonPath("$.quietHoursStart").value("22:00:00"))
                .andExpect(jsonPath("$.quietHoursEnd").value("08:00:00"))
                .andExpect(jsonPath("$.timezone").value("UTC"));
    }

    /**
     * 알림 설정의 소유자와 JWT 인증에 사용할 사용자를 저장.
     */
    private User saveUser(String username) {
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .build());
    }

    /**
     * 설정 소유 사용자의 식별자와 역할을 담은 Bearer 토큰을 생성.
     */
    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    }
}
