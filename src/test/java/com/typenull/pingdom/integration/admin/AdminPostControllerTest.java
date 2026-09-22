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
import com.typenull.pingdom.identity.domain.admin.AdminRole;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignment;
import com.typenull.pingdom.identity.domain.repository.AdminRoleAssignmentRepository;
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
import java.time.LocalDateTime;
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

/**
 * 게시물 삭제의 DB·outbox·감사 기록과 고아 객체 삭제 확인 입력 계약을 검증. S3는 대역을 사용.
 */
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
    private AdminRoleAssignmentRepository adminRoleAssignmentRepository;

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

    /**
     * 감사 로그·outbox·게시물·관리자 역할을 사용자보다 먼저 비워 삭제 부작용 검증을 격리.
     */
    @BeforeEach
    void setUp() {
        adminAuditLogRepository.deleteAllInBatch();
        outboxEventRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
        adminRoleAssignmentRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    /**
     * 관리자 인증 이후에도 제거된 게시물 목록·상세 조회 경로가 404인지 확인.
     */
    @Test
    void removedPostQueries() throws Exception {
        String adminAccessToken = createAdminAndLogin();

        mockMvc.perform(get("/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/admin/posts/{id}", 1L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isNotFound());
    }

    /**
     * 게시물 삭제가 DB 행 제거, S3 삭제 요청 outbox 한 건, 대상 게시물 감사 로그로 이어지는지 확인. 실제 S3 삭제 완료는 검증 범위에서 제외.
     */
    @Test
    void deletePostOutboxAndAudit() throws Exception {
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

    /**
     * 리포트 ID와 확인 플래그 없이 키만 제출한 고아 객체 삭제 요청을 400으로 거절하는지 확인.
     */
    @Test
    void orphanDeleteNeedsConfirmation() throws Exception {
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

    /**
     * 확인된 빈 키 목록 요청에서 요청·삭제·실패 건수가 모두 0인지 확인.
     */
    @Test
    void confirmedEmptyOrphanDelete() throws Exception {
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

    /**
     * S3 삭제 요청 한 건의 aggregate와 payload 키·사유가 삭제된 게시물과 일치하는지 확인.
     */
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

    /**
     * 삭제할 게시물의 일반 사용자 소유자를 암호화된 비밀번호와 함께 저장.
     */
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

    /**
     * 고유 이름의 관리자와 SUPER_ADMIN 배정을 저장한 뒤 실제 로그인으로 접근 토큰을 생성.
     */
    private String createAdminAndLogin() throws Exception {
        String username = "adminTester" + System.nanoTime();
        User admin = userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.ADMIN)
                .build());
        adminRoleAssignmentRepository.save(AdminRoleAssignment.assign(
                admin.getId(), AdminRole.SUPER_ADMIN, admin.getId(), LocalDateTime.now()
        ));

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

    /**
     * URL과 별도 S3 키를 가진 게시물을 저장해 삭제 요청이 키를 사용하는지 검증할 fixture를 생성.
     */
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
