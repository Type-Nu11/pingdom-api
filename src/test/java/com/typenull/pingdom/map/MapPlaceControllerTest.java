package com.typenull.pingdom.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.domain.auth.dto.login.LoginRequest;
import com.typenull.pingdom.domain.auth.dto.signup.SignupRequest;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import com.typenull.pingdom.domain.map.domain.MapPlace;
import com.typenull.pingdom.domain.map.repository.MapPlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MapPlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MapPlaceRepository mapPlaceRepository;

    @BeforeEach
    void setUp() {
        mapPlaceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createRegistersPlace() throws Exception {
        String accessToken = signupAndLogin("place01");

        mockMvc.perform(post("/map/places/create")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "테스트 장소",
                                  "address": "서울특별시 강남구 테헤란로 1",
                                  "latitude": 37.501,
                                  "longitude": 127.039
                                }
                                """))
                .andExpect(status().isCreated());

        assertEquals(1L, mapPlaceRepository.count());
        MapPlace saved = mapPlaceRepository.findAll().get(0);
        assertEquals("테스트 장소", saved.getName());
        assertEquals("서울특별시 강남구 테헤란로 1", saved.getAddress());
        assertEquals(37.501, saved.getLatitude());
        assertEquals(127.039, saved.getLongitude());
    }

    @Test
    void createFailsWhenNameIsBlank() throws Exception {
        String accessToken = signupAndLogin("place02");

        mockMvc.perform(post("/map/places/create")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   ",
                                  "address": "서울특별시",
                                  "latitude": 37.0,
                                  "longitude": 127.0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("장소 이름은 필수입니다."));
    }

    @Test
    void deleteRemovesOwnedPlace() throws Exception {
        String accessToken = signupAndLogin("place03");
        long userId = userRepository.findAll().get(0).getId();
        MapPlace place = createPlace(userId);

        mockMvc.perform(delete("/map/places/{id}/delete", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        assertEquals(0L, mapPlaceRepository.count());
    }

    @Test
    void deleteFailsWhenPlaceDoesNotExist() throws Exception {
        String accessToken = signupAndLogin("place04");

        mockMvc.perform(delete("/map/places/{id}/delete", 9999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    void deleteFailsWhenUserDeletesOthersPlace() throws Exception {
        String ownerToken = signupAndLogin("place05-owner");
        long ownerId = userRepository.findAll().get(0).getId();
        MapPlace place = createPlace(ownerId);

        String otherToken = signupAndLogin("place05-other");

        mockMvc.perform(delete("/map/places/{id}/delete", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OTHERS_PLACE_NOT_DELETED"));

        assertEquals(1L, mapPlaceRepository.count());
    }

    private MapPlace createPlace(long userId) {
        return mapPlaceRepository.save(
                MapPlace.builder()
                        .name("owner-place")
                        .address("somewhere")
                        .latitude(37.5)
                        .longitude(127.0)
                        .userId(userId)
                        .build()
        );
    }

    private String signupAndLogin(String username) throws Exception {
        SignupRequest signupRequest = new SignupRequest(
                username,
                "tester",
                username + "@example.com",
                "password123"
        );

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

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

