package com.typenull.pingdom.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.post.domain.repository.MapImageRepository;
import com.typenull.pingdom.place.domain.repository.MapPlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AdminMapPlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MapPlaceRepository mapPlaceRepository;

    @Autowired
    private MapImageRepository mapImageRepository;

    @BeforeEach
    void setUp() {
        mapImageRepository.deleteAll();
        mapPlaceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void listPlacesReturnsRegisteredPlaces() throws Exception {
        String accessToken = createAdminAndLogin();
        mapPlaceRepository.save(MapPlace.builder()
                .name("진주성")
                .address("경상남도 진주시 남강로 626")
                .latitude(35.1894)
                .longitude(128.0789)
                .userId(11L)
                .build());

        mockMvc.perform(get("/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].name").value("진주성"))
                .andExpect(jsonPath("$.places[0].address").value("경상남도 진주시 남강로 626"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void getPlaceReturnsPlaceAndLinkedPosts() throws Exception {
        String accessToken = createAdminAndLogin();
        User placeOwner = userRepository.save(User.builder()
                .username("placeOwner")
                .email("place-owner@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1997)
                .language("ko")
                .country("KR")
                .role(UserRole.USER)
                .build());

        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("남강")
                .address("경상남도 진주시 남강변")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(placeOwner.getId())
                .build());

        mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/namgang.jpg")
                .s3Key("map/namgang.jpg")
                .title("남강 야경")
                .description("강변에서 촬영한 사진")
                .userId(15L)
                .username("placeOwner")
                .likeCount(7L)
                .mapPlace(mapPlace)
                .build());

        mockMvc.perform(get("/admin/places/{id}", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mapPlace.getId()))
                .andExpect(jsonPath("$.name").value("남강"))
                .andExpect(jsonPath("$.username").value("placeOwner"))
                .andExpect(jsonPath("$.sortParam").value(SortParam.LATEST.name()))
                .andExpect(jsonPath("$.postCount").value(1))
                .andExpect(jsonPath("$.posts[0].title").value("남강 야경"))
                .andExpect(jsonPath("$.posts[0].likeCount").value(7))
                .andExpect(jsonPath("$.posts[0].username").value("placeOwner"));
    }

    @Test
    void getPlaceSortsPostsByMostLiked() throws Exception {
        String accessToken = createAdminAndLogin();
        User placeOwner = userRepository.save(User.builder()
                .username("placeSortOwner")
                .email("place-sort-owner@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1996)
                .language("ko")
                .country("KR")
                .role(UserRole.USER)
                .build());

        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("촉석루")
                .address("경상남도 진주시 본성동")
                .latitude(35.1880)
                .longitude(128.0815)
                .userId(placeOwner.getId())
                .build());

        mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/low-like.jpg")
                .s3Key("map/low-like.jpg")
                .title("좋아요 적은 사진")
                .description("첫 번째 사진")
                .userId(placeOwner.getId())
                .username("placeSortOwner")
                .likeCount(2L)
                .mapPlace(mapPlace)
                .build());

        mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/high-like.jpg")
                .s3Key("map/high-like.jpg")
                .title("좋아요 많은 사진")
                .description("두 번째 사진")
                .userId(placeOwner.getId())
                .username("placeSortOwner")
                .likeCount(9L)
                .mapPlace(mapPlace)
                .build());

        mockMvc.perform(get("/admin/places/{id}", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("sortParam", SortParam.MOST_LIKED.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortParam").value(SortParam.MOST_LIKED.name()))
                .andExpect(jsonPath("$.posts[0].title").value("좋아요 많은 사진"))
                .andExpect(jsonPath("$.posts[0].likeCount").value(9))
                .andExpect(jsonPath("$.posts[1].title").value("좋아요 적은 사진"));
    }

    @Test
    void getPlaceReturnsNotFoundWhenPlaceDoesNotExist() throws Exception {
        String accessToken = createAdminAndLogin();

        mockMvc.perform(get("/admin/places/{id}", 9999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    private String createAdminAndLogin() throws Exception {
        userRepository.save(User.builder()
                .username("adminPlaceTester")
                .email("admin-place@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.ADMIN)
                .build());

        LoginRequest loginRequest = new LoginRequest("adminPlaceTester", "password123");
        MvcResult loginResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .textValue();
    }
}
