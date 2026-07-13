package com.typenull.pingdom.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.event.PlaceEventRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
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
class PlaceEventControllerTest {

    private static final AtomicInteger ADMIN_SEQUENCE = new AtomicInteger();

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
    private PlaceEventRepository placeEventRepository;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    private MapPlace place;

    @BeforeEach
    void setUp() {
        adminAuditLogRepository.deleteAllInBatch();
        placeEventRepository.deleteAllInBatch();
        mapPlaceRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        place = mapPlaceRepository.save(MapPlace.builder()
                .name("진주성")
                .address("경상남도 진주시 남강로 626")
                .latitude(35.1801)
                .longitude(128.1078)
                .registrant("admin")
                .build());
    }

    @Test
    void adminPublishesEventAndAppReadsOnlyPublishedEvent() throws Exception {
        String accessToken = createAdminAndLogin();
        LocalDateTime startAt = LocalDateTime.now().plusDays(1).withNano(0);
        LocalDateTime endAt = startAt.plusDays(7);

        MvcResult createResult = mockMvc.perform(post("/admin/place-events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventRequest(startAt, endAt))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placeId").value(place.getId()))
                .andExpect(jsonPath("$.publicationStatus").value("DRAFT"))
                .andExpect(jsonPath("$.scheduleStatus").value("UPCOMING"))
                .andReturn();
        long eventId = readEventId(createResult);

        mockMvc.perform(get("/events/{eventId}", eventId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_EVENT_NOT_FOUND"));

        mockMvc.perform(post("/admin/place-events/{eventId}/publish", eventId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"운영 검토 완료\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicationStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.scheduleStatus").value("UPCOMING"));

        mockMvc.perform(get("/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("eventType", "EXHIBITION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.events[0].id").value(eventId))
                .andExpect(jsonPath("$.events[0].scheduleStatus").value("UPCOMING"));

        mockMvc.perform(get("/events/{eventId}", eventId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(place.getId()))
                .andExpect(jsonPath("$.description").value("남강 야간 전시와 공연"));

        assertThat(adminAuditLogRepository.findAll())
                .extracting(log -> log.getAction())
                .containsExactlyInAnyOrder(AdminAuditAction.PLACE_EVENT_CREATED, AdminAuditAction.PLACE_EVENT_PUBLISHED);
    }

    @Test
    void doesNotAllowUpdatingPublishedEvent() throws Exception {
        String accessToken = createAdminAndLogin();
        LocalDateTime startAt = LocalDateTime.now().plusDays(1).withNano(0);
        long eventId = createAndPublish(accessToken, startAt, startAt.plusDays(2));

        mockMvc.perform(patch("/admin/place-events/{eventId}", eventId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventRequest(startAt, startAt.plusDays(3)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLACE_EVENT_UPDATE_NOT_ALLOWED"));
    }

    @Test
    void cancellingPublishedEventRemovesItFromAppDiscovery() throws Exception {
        String accessToken = createAdminAndLogin();
        LocalDateTime startAt = LocalDateTime.now().plusDays(1).withNano(0);
        long eventId = createAndPublish(accessToken, startAt, startAt.plusDays(2));

        mockMvc.perform(post("/admin/place-events/{eventId}/cancel", eventId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"행사 주최 측 취소\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicationStatus").value("CANCELLED"));

        mockMvc.perform(get("/events/{eventId}", eventId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void doesNotDeletePlaceWithLinkedEvent() throws Exception {
        String accessToken = createAdminAndLogin();
        LocalDateTime startAt = LocalDateTime.now().plusDays(1).withNano(0);
        createAndPublish(accessToken, startAt, startAt.plusDays(2));

        mockMvc.perform(delete("/admin/places/{id}/delete", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLACE_EVENT_CONNECTED"));
    }

    @Test
    void capsOversizedEventListPage() throws Exception {
        String accessToken = createAdminAndLogin();

        mockMvc.perform(get("/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", String.valueOf(Integer.MAX_VALUE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(10_000));
    }

    private long createAndPublish(String accessToken, LocalDateTime startAt, LocalDateTime endAt) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/admin/place-events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventRequest(startAt, endAt))))
                .andExpect(status().isCreated())
                .andReturn();
        long eventId = readEventId(createResult);
        mockMvc.perform(post("/admin/place-events/{eventId}/publish", eventId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"운영 검토 완료\"}"))
                .andExpect(status().isOk());
        return eventId;
    }

    private ObjectNode eventRequest(LocalDateTime startAt, LocalDateTime endAt) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("placeId", place.getId());
        request.put("title", "진주 여름 빛 축제");
        request.put("description", "남강 야간 전시와 공연");
        request.put("eventType", "EXHIBITION");
        request.put("startAt", startAt.toString());
        request.put("endAt", endAt.toString());
        request.put("reason", "공식 일정 등록");
        return request;
    }

    private long readEventId(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("eventId").longValue();
    }

    private String createAdminAndLogin() throws Exception {
        String username = "eventAdmin" + ADMIN_SEQUENCE.incrementAndGet();
        userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.ADMIN)
                .build());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .textValue();
    }
}
