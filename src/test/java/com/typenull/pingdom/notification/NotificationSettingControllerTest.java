package com.typenull.pingdom.notification;

import com.typenull.pingdom.notification.api.dto.settings.NotificationSettingUpdateRequest;
import com.typenull.pingdom.notification.repository.NotificationSettingRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

    @BeforeEach
    void setUp() {
        notificationSettingRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void getSettingReturnsDefaultSettingWithoutCreatingRow() throws Exception {
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

    @Test
    void updateSettingRejectsQuietHoursWithoutTimes() throws Exception {
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

    @Test
    void updateSettingChangesNotificationPreferences() throws Exception {
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

    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    }
}
