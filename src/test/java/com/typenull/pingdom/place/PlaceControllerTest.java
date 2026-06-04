package com.typenull.pingdom.place;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.cloud.aws.s3.bucket=test-bucket",
        "spring.cloud.aws.region.static=ap-northeast-2",
        "spring.cloud.aws.credentials.access-key=test-access-key",
        "spring.cloud.aws.credentials.secret-key=test-secret-key"
})
@AutoConfigureMockMvc
@Transactional
class PlaceControllerTest {

    @TestConfiguration
    static class TestEmailSenderConfig {
        @Bean
        @Primary
        EmailSender emailSender() {
            return (recipientEmail, verificationCode) -> {};
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MapPlaceRepository mapPlaceRepository;

    @org.springframework.boot.test.mock.mockito.MockBean
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        mapPlaceRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void listPlacesReturnsPagedPlaces() throws Exception {
        String accessToken = signupAndLogin("reader01");
        createMapPlace("첫 번째 장소", "경상남도 진주시 진양호로 1");
        createMapPlace("두 번째 장소", "경상남도 진주시 남강로 2");

        mockMvc.perform(get("/place")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.places.length()").value(2))
                .andExpect(jsonPath("$.places[0].name").value("두 번째 장소"))
                .andExpect(jsonPath("$.places[1].name").value("첫 번째 장소"));
    }

    @Test
    void getPlaceReturnsPlaceDetailOnly() throws Exception {
        String accessToken = signupAndLogin("reader02");
        MapPlace mapPlace = createMapPlace("진주성", "경상남도 진주시 남강로 626");

        mockMvc.perform(get("/place/{id}", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mapPlace.getId()))
                .andExpect(jsonPath("$.name").value("진주성"))
                .andExpect(jsonPath("$.address").value("경상남도 진주시 남강로 626"))
                .andExpect(jsonPath("$.registrant").value("placeOwner"));
    }

    @Test
    void getPlaceReturnsNotFoundWhenPlaceDoesNotExist() throws Exception {
        String accessToken = signupAndLogin("reader03");

        mockMvc.perform(get("/place/{id}", 9999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    private String signupAndLogin(String username) throws Exception {
        SignupRequest signupRequest = new SignupRequest(username, username + "@example.com", "password123", 1998, null, "ko", "KR");

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

    private MapPlace createMapPlace(String name, String address) {
        return mapPlaceRepository.save(MapPlace.builder()
                .name(name)
                .address(address)
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(1L)
                .registrant("placeOwner")
                .build());
    }

}
