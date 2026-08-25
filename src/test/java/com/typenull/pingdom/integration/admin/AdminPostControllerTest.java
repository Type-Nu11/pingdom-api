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
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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

@Tag("integration")
@SpringBootTest(properties = {
        "spring.cloud.aws.s3.bucket=test-bucket",
        "spring.cloud.aws.s3.public-base-url=https://cdn.pingdom.test",
        "spring.cloud.aws.region.static=ap-northeast-2",
        "spring.cloud.aws.credentials.access-key=test-access-key",
        "spring.cloud.aws.credentials.secret-key=test-secret-key",
        "spring.security.oauth2.client.registration.google.client-id=test-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-client-secret",
        "fcm.enabled=false",
        "fcm.key-path=dummy",
        "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
@Transactional
class AdminPostControllerTest {

    @MockBean
    private S3Client s3Client;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MapImageRepository mapImageRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        adminAuditLogRepository.deleteAllInBatch();
        outboxEventRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void removedPostQueryEndpointsReturnNotFound() throws Exception {
        String adminAccessToken = createAdminAndLogin();

        mockMvc.perform(get("/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/admin/posts/{id}", 1L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePostRemovesDatabaseRecordAndCreatesS3DeleteOutboxEvent() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("deleteTargetOwner");
        MapImage mapImage = createMapImage(owner.getId(), owner.getUsername(), "https://example.com/delete-target.jpg");

        mockMvc.perform(delete("/admin/posts/{id}/delete", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isNoContent());

        assertEquals(0L, mapImageRepository.count());
        assertS3DeleteOutboxEvent(mapImage.getId(), "test-key-" + owner.getId(), "ADMIN_MAP_IMAGE_DELETED");
        assertEquals(1, adminAuditLogRepository.findAll().size());
        assertEquals(AdminAuditAction.POST_DELETED, adminAuditLogRepository.findAll().getFirst().getAction());
        assertEquals(AdminAuditTargetType.POST, adminAuditLogRepository.findAll().getFirst().getTargetType());
        assertEquals(String.valueOf(mapImage.getId()), adminAuditLogRepository.findAll().getFirst().getTargetId());
    }

    @Test
    void deleteS3OrphansRequiresReportConfirmation() throws Exception {
        String adminAccessToken = createAdminAndLogin();

        mockMvc.perform(delete("/admin/posts/s3/orphans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "keys": ["map/orphan.jpg"]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteS3OrphansAcceptsConfirmedRequest() throws Exception {
        String adminAccessToken = createAdminAndLogin();

        mockMvc.perform(delete("/admin/posts/s3/orphans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportId": "report-1",
                                  "keys": [],
                                  "confirmed": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedKeyCount").value(0))
                .andExpect(jsonPath("$.deletedKeyCount").value(0))
                .andExpect(jsonPath("$.failedKeyCount").value(0));
    }

    private void assertS3DeleteOutboxEvent(Long mapImageId, String s3Key, String reason) throws Exception {
        List<OutboxEvent> events = outboxEventRepository.findAll()
                .stream()
                .filter(event -> event.getEventType() == OutboxEventType.S3_OBJECT_DELETE_REQUESTED)
                .toList();
        assertEquals(1, events.size());
        OutboxEvent event = events.get(0);
        assertEquals(OutboxEventType.S3_OBJECT_DELETE_REQUESTED, event.getEventType());
        assertEquals("MAP_IMAGE", event.getAggregateType());
        assertEquals(String.valueOf(mapImageId), event.getAggregateId());
        assertEquals(s3Key, objectMapper.readTree(event.getPayload()).get("s3Key").asText());
        assertEquals(reason, objectMapper.readTree(event.getPayload()).get("reason").asText());
    }

    private User createUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.USER)
                .build());
    }

    private String createAdminAndLogin() throws Exception {
        String username = "adminTester" + System.nanoTime();
        userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.ADMIN)
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

    private MapImage createMapImage(Long userId, String username, String imageUrl) {
        return mapImageRepository.save(MapImage.builder()
                .imageUrl(imageUrl)
                .s3Key("test-key-" + userId)
                .title("신고 대상 제목")
                .description("신고 대상 설명")
                .userId(userId)
                .username(username)
                .build());
    }
}
