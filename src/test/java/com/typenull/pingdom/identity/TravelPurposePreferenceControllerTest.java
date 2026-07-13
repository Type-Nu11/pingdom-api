package com.typenull.pingdom.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.profile.TravelPurposePreferenceUpdateRequest;
import com.typenull.pingdom.identity.domain.TravelPurpose;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.JwtTokenProvider;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

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

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_travel_purpose");
        userRepository.deleteAllInBatch();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM user_travel_purpose");
        userRepository.deleteAllInBatch();
    }

    @Test
    void getTravelPurposesReturnsEmptyListForUserWithoutPreferences() throws Exception {
        User user = saveUser("travelPurposeReader");

        mockMvc.perform(get("/users/me/travel-purposes")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.travelPurposes").isEmpty());
    }

    @Test
    void replaceTravelPurposesReplacesEntirePreferenceSet() throws Exception {
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

    @Test
    void replaceTravelPurposesRejectsMissingPreferenceList() throws Exception {
        User user = saveUser("travelPurposeInvalid");

        mockMvc.perform(put("/users/me/travel-purposes")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.travelPurposes").value("여행 목적 선호 목록은 필수입니다."));
    }

    @Test
    void travelPurposeEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/users/me/travel-purposes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withdrawalDeletesTravelPurposesImmediately() throws Exception {
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
