package com.typenull.pingdom.identity;

import com.typenull.pingdom.identity.api.dto.profile.TravelPurposePreferenceUpdateRequest;
import com.typenull.pingdom.identity.domain.TravelPurpose;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class TravelPurposePreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 여행 목적 연결 테이블을 먼저 비우고 사용자를 제거해 외래 키와 이전 선호 데이터의 영향을 없앤다.
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_travel_purpose");
        userRepository.deleteAllInBatch();
    }

    /**
     * 요청으로 저장한 여행 목적 선호와 사용자를 의존 순서대로 정리한다.
     */
    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM user_travel_purpose");
        userRepository.deleteAllInBatch();
    }

    /**
     * 선호를 등록하지 않은 인증 사용자의 여행 목적 조회가 200과 빈 목록을 반환하는지 검증한다.
     */
    @Test
    void returnsEmptyTravelPreferences() throws Exception {
        User user = saveUser("travelPurposeReader");

        mockMvc.perform(get("/users/me/travel-purposes")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.travelPurposes").isEmpty());
    }

    /**
     * 두 여행 목적을 저장한 응답의 항목을 확인한 뒤 빈 목록으로 다시 요청하면 전체 선호가 비워지는지 검증한다.
     */
    @Test
    void replacesTravelPreferenceSet() throws Exception {
        User user = saveUser("travelPurposeUpdater");
        TravelPurposePreferenceUpdateRequest request = new TravelPurposePreferenceUpdateRequest(
                new LinkedHashSet<>(Set.of(TravelPurpose.K_POP, TravelPurpose.FOOD))
        );

        mockMvc.perform(put("/users/me/travel-purposes")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.travelPurposes").isArray())
                .andExpect(jsonPath("$.travelPurposes.length()").value(2))
                .andExpect(jsonPath("$.travelPurposes").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "K_POP", "FOOD"
                )));

        mockMvc.perform(put("/users/me/travel-purposes")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"travelPurposes\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.travelPurposes").isEmpty());
    }

    /**
     * 여행 목적 필드가 없는 수정 요청은 400과 필수 목록 검증 메시지를 반환하는지 검증한다.
     */
    @Test
    void rejectsMissingTravelPreferences() throws Exception {
        User user = saveUser("travelPurposeInvalid");

        mockMvc.perform(put("/users/me/travel-purposes")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.travelPurposes").value("여행 목적 선호 목록은 필수입니다."));
    }

    /**
     * 인증 없이 여행 목적 목록을 조회하면 401을 반환하는지 검증한다.
     */
    @Test
    void requiresTravelPreferenceAuthentication() throws Exception {
        mockMvc.perform(get("/users/me/travel-purposes"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 여행 목적을 등록한 사용자가 탈퇴하면 204를 반환하고 연결 테이블의 해당 사용자 선호가 즉시 제거되는지 검증한다.
     */
    @Test
    void deletesTravelPreferencesOnWithdrawal() throws Exception {
        User user = saveUser("travelPurposeWithdrawal");
        TravelPurposePreferenceUpdateRequest request = new TravelPurposePreferenceUpdateRequest(
                Set.of(TravelPurpose.BEAUTY)
        );

        mockMvc.perform(put("/users/me/travel-purposes")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isNoContent());

        Integer preferenceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_travel_purpose WHERE user_id = ?",
                Integer.class,
                user.getId()
        );
        assertThat(preferenceCount).isZero();
    }

    /**
     * 여행 목적 API에서 인증하고 조회할 독립 사용자를 저장한다.
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
     * 사용자 식별자와 현재 역할을 담아 여행 목적 API의 Bearer 인증 토큰을 만든다.
     */
    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    }
}
