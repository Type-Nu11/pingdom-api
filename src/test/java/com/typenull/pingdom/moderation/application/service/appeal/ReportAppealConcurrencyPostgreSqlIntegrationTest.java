package com.typenull.pingdom.moderation.application.service.appeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.domain.appeal.ReportAppeal;
import com.typenull.pingdom.moderation.domain.appeal.ReportAppealStatus;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import com.typenull.pingdom.moderation.infrastructure.persistence.ReportAppealRepository;
import com.typenull.pingdom.moderation.infrastructure.persistence.UserSanctionHistoryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false", "spring.flyway.enabled=true",
        "spring.flyway.postgresql.transactional-lock=false",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.cloud.aws.s3.enabled=false",
        "management.health.redis.enabled=false", "fcm.enabled=false", "outbox.enabled=false"
})
class ReportAppealConcurrencyPostgreSqlIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private ReportAppealService service;
    @Autowired private ReportAppealRepository appeals;
    @Autowired private PostReportRepository reports;
    @Autowired private MapImageRepository images;
    @Autowired private MapPlaceRepository places;
    @Autowired private UserRepository users;
    @Autowired private UserSanctionHistoryRepository sanctions;
    @Autowired private OutboxEventRepository outbox;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @SpyBean private AdminAuditLogRepository audit;
    // 실제 권한 행렬은 ReportModerationPermissionTest에서 검증하며 여기서는 DB 경합에 집중합니다.
    @MockBean private AdminRoleAuthorizationService authorization;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE report_appeal, post_report, map_image, map_place, users, "
                + "admin_audit_log, user_sanction_history, outbox_event RESTART IDENTITY CASCADE");
    }

    @Test
    void approvalWinsOverConcurrentRejectionAndKeepsAllEffectsConsistent() throws Exception {
        assertCompetingDecision(ReportAppealStatus.APPROVED, false);
    }

    @Test
    void onlyOneApprovalCanCommitForTheSameAppeal() throws Exception {
        assertCompetingDecision(ReportAppealStatus.APPROVED, true);
    }

    @Test
    void rejectionWinsWithoutLeavingRestorationEffects() throws Exception {
        assertCompetingDecision(ReportAppealStatus.REJECTED, true);
    }

    @Test
    void auditFailureRollsBackReportPhotoSanctionAndDecisionTogether() {
        Fixture fixture = fixture();
        doThrow(new DataIntegrityViolationException("forced appeal audit failure"))
                .when(audit).save(argThat(row -> row != null && row.getAction() == AdminAuditAction.APPEAL_APPROVED));

        assertThatThrownBy(() -> service.approve(fixture.appealId(), "restore", fixture.adminId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertState(fixture, ReportAppealStatus.SUBMITTED, PostReportStatus.ACCEPTED,
                MapImageVisibilityStatus.AUTO_HIDDEN, true, 0);
        assertThat(audit.count()).isZero();
        assertThat(sanctions.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @Test
    void differentAppealsForOnePhotoIncrementPhotoCountAndReleaseSanctionOnce() throws Exception {
        Fixture first = fixture();
        long secondAppealId = new TransactionTemplate(transactions).execute(status -> {
            User reporter = users.saveAndFlush(user("other-reporter"));
            MapImage image = images.findById(first.postId()).orElseThrow();
            PostReport report = reports.saveAndFlush(report(image, reporter, first.targetId()));
            return appeals.saveAndFlush(appeal(report, first.targetId())).getId();
        });
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> one = executor.submit(() -> {
                await(start);
                service.approve(first.appealId(), "restore", first.adminId());
            });
            Future<?> two = executor.submit(() -> {
                await(start);
                service.approve(secondAppealId, "restore", first.adminId());
            });
            start.countDown();
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);

            assertState(first, ReportAppealStatus.APPROVED, PostReportStatus.RESTORED,
                    MapImageVisibilityStatus.ACTIVE, false, 1);
            assertThat(appeals.findById(secondAppealId).orElseThrow().getStatus()).isEqualTo(ReportAppealStatus.APPROVED);
            assertThat(sanctions.count()).isEqualTo(1);
            assertThat(outbox.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertCompetingDecision(ReportAppealStatus firstDecision, boolean followerApproves) throws Exception {
        Fixture fixture = fixture();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch followerStarted = new CountDownLatch(1);
        AtomicReference<Future<Throwable>> follower = new AtomicReference<>();
        try {
            // 첫 처리의 모든 DB 부작용을 반영한 채 커밋을 보류하여 두 번째 요청의 잠금 대기를 제어합니다.
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                if (firstDecision == ReportAppealStatus.APPROVED) {
                    service.approve(fixture.appealId(), "restore", fixture.adminId());
                } else {
                    service.reject(fixture.appealId(), "reject", fixture.adminId());
                }
                follower.set(executor.submit(() -> {
                    followerStarted.countDown();
                    try {
                        if (followerApproves) {
                            service.approve(fixture.appealId(), "late", fixture.adminId());
                        } else {
                            service.reject(fixture.appealId(), "late", fixture.adminId());
                        }
                        return null;
                    } catch (Throwable failure) {
                        return failure;
                    }
                }));
                await(followerStarted);
                assertThatThrownBy(() -> follower.get().get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            });

            assertThat(follower.get().get(5, TimeUnit.SECONDS)).isInstanceOfSatisfying(AdminException.class,
                    failure -> assertThat(failure.getErrorCode()).isEqualTo(AdminErrorCode.APPEAL_ALREADY_PROCESSED));
            boolean approved = firstDecision == ReportAppealStatus.APPROVED;
            assertState(fixture, firstDecision, approved ? PostReportStatus.RESTORED : PostReportStatus.ACCEPTED,
                    approved ? MapImageVisibilityStatus.ACTIVE : MapImageVisibilityStatus.AUTO_HIDDEN,
                    !approved, approved ? 1 : 0);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE target_type = 'APPEAL'",
                    Long.class)).isEqualTo(1);
            assertThat(sanctions.count()).isEqualTo(approved ? 1 : 0);
            assertThat(outbox.count()).isEqualTo(approved ? 1 : 0);
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertState(Fixture fixture, ReportAppealStatus appealStatus, PostReportStatus reportStatus,
                             MapImageVisibilityStatus visibility, boolean banned, long photoCount) {
        assertThat(appeals.findById(fixture.appealId()).orElseThrow().getStatus()).isEqualTo(appealStatus);
        assertThat(reports.findById(fixture.reportId()).orElseThrow().getStatus()).isEqualTo(reportStatus);
        assertThat(images.findById(fixture.postId()).orElseThrow().getVisibilityStatus()).isEqualTo(visibility);
        assertThat(users.findById(fixture.targetId()).orElseThrow().isBanned()).isEqualTo(banned);
        assertThat(jdbc.queryForObject("SELECT photo_count FROM map_place WHERE map_place_id = ?",
                Long.class, fixture.placeId())).isEqualTo(photoCount);
    }

    private Fixture fixture() {
        return new TransactionTemplate(transactions).execute(status -> {
            User admin = users.saveAndFlush(User.builder().username("admin").email("admin@example.com")
                    .password("password").birthYear(1998).language("ko").country("KR").role(UserRole.ADMIN).build());
            User target = user("target");
            target.ban("report", LocalDateTime.now().minusHours(1), null);
            users.saveAndFlush(target);
            User reporter = users.saveAndFlush(user("reporter"));
            Long placeId = jdbc.queryForObject("""
                    INSERT INTO map_place (place_name, address, latitude, longitude, registrant, photo_count)
                    VALUES ('이의제기 장소', '서울', 37.5, 127.0, 'test', 0) RETURNING map_place_id
                    """, Long.class);
            MapImage image = MapImage.builder().imageUrl("https://example.com/photo").s3Key("photo")
                    .title("photo").userId(target.getId()).username(target.getUsername())
                    .mapPlace(places.findById(placeId).orElseThrow()).build();
            image.autoHide("report", LocalDateTime.now(), admin.getId());
            images.saveAndFlush(image);
            PostReport report = reports.saveAndFlush(report(image, reporter, target.getId()));
            ReportAppeal appeal = appeals.saveAndFlush(appeal(report, target.getId()));
            return new Fixture(admin.getId(), target.getId(), placeId, image.getId(), report.getId(), appeal.getId());
        });
    }

    private User user(String name) {
        return User.builder().username(name).email(name + "@example.com")
                .password("password").birthYear(1998).language("ko").country("KR").build();
    }

    private PostReport report(MapImage image, User reporter, long targetId) {
        return PostReport.builder().reporterUserId(reporter.getId()).reporterUsername(reporter.getUsername())
                .reportedUserId(targetId).reportedImageId(image.getId()).reportedImageUrl(image.getImageUrl())
                .mapImage(image).reason("report").status(PostReportStatus.ACCEPTED).build();
    }

    private ReportAppeal appeal(PostReport report, long targetId) {
        return ReportAppeal.builder().reportId(report.getId()).postId(report.getReportedImageId())
                .targetUserId(targetId).appellantUserId(targetId).appellantUsername("target").reason("appeal").build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동기화 지점 대기 시간 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동기화 대기 중 인터럽트", exception);
        }
    }

    private record Fixture(long adminId, long targetId, long placeId, long postId, long reportId, long appealId) {
    }
}
