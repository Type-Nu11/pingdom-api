package com.typenull.pingdom.moderation.application.service.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
class AdminPostDeletionConcurrencyPostgreSqlIntegrationTest {

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

    @Autowired private AdminPostServiceImpl service;
    @SpyBean private MapImageRepository images;
    @Autowired private MapPlaceRepository places;
    @Autowired private PostReportRepository reports;
    @Autowired private UserRepository users;
    @Autowired private AdminAuditLogRepository audit;
    @Autowired private OutboxEventRepository outbox;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @MockBean private AdminRoleAuthorizationService authorization;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE report_appeal, post_report, map_image, map_place, users, "
                + "admin_audit_log, outbox_event RESTART IDENTITY CASCADE");
    }

    @Test
    void deletionWaitsForHideAndDoesNotDecrementHiddenPhotoAgain() throws Exception {
        assertDeletionAfterVisibilityChange(false);
    }

    @Test
    void deletionWaitsForRestoreAndDecrementsFreshActiveState() throws Exception {
        assertDeletionAfterVisibilityChange(true);
    }

    @Test
    void concurrentDuplicateDeletionPublishesOnlyWinningEffects() throws Exception {
        Fixture fixture = fixture();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Future<Throwable>> follower = new AtomicReference<>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                service.deletePost(fixture.imageId(), fixture.userId());
                follower.set(executor.submit(() -> delete(fixture, started)));
                await(started);
                assertThatThrownBy(() -> follower.get().get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            });

            assertThat(follower.get().get(5, TimeUnit.SECONDS)).isInstanceOfSatisfying(AdminException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(AdminErrorCode.POST_NOT_FOUND));
            assertRemainingPhoto(fixture);
            assertThat(outbox.count()).isEqualTo(2L);
            assertThat(audit.count()).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void placeReassignmentAfterMetadataRollsBackDetachedReports() throws Exception {
        Fixture fixture = fixture();
        MapPlace target = places.saveAndFlush(place("target", 0L));
        CountDownLatch referenceRead = new CountDownLatch(1);
        CountDownLatch moved = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            // 삭제 진입의 존재 확인 다음, 공통 helper가 읽은 장소 참조를 재배치 전에 고정합니다.
            if (reads.incrementAndGet() == 2) {
                referenceRead.countDown();
                await(moved);
            }
            return result;
        }).when(images).findPlaceReferenceById(fixture.imageId());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Throwable> deletion = executor.submit(() -> delete(fixture, new CountDownLatch(0)));
            await(referenceRead);
            try {
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    MapPlace source = places.findByIdForUpdate(fixture.placeId()).orElseThrow();
                    MapPlace lockedTarget = places.findByIdForUpdate(target.getId()).orElseThrow();
                    MapImage image = images.findByIdForUpdate(fixture.imageId()).orElseThrow();
                    image.reassignPlace(lockedTarget);
                    source.decreasePhotoCount();
                    lockedTarget.increasePhotoCount();
                });
            } finally {
                moved.countDown();
            }

            assertThat(deletion.get(5, TimeUnit.SECONDS)).isInstanceOfSatisfying(MapException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(MapErrorCode.IMAGE_PLACE_CHANGED));
            assertThat(images.existsById(fixture.imageId())).isTrue();
            assertThat(reports.findById(fixture.reportId()).orElseThrow().getMapImage().getId())
                    .isEqualTo(fixture.imageId());
            assertThat(places.findById(fixture.placeId()).orElseThrow().currentPhotoCount()).isEqualTo(1L);
            assertThat(places.findById(target.getId()).orElseThrow().currentPhotoCount()).isEqualTo(1L);
            assertThat(outbox.count()).isZero();
            assertThat(audit.count()).isZero();
        } finally {
            moved.countDown();
            executor.shutdownNow();
        }
    }

    private void assertDeletionAfterVisibilityChange(boolean restore) throws Exception {
        Fixture fixture = fixture();
        if (restore) service.hidePost(fixture.imageId(), "initial hide", fixture.userId());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Future<Throwable>> follower = new AtomicReference<>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                if (restore) service.restorePost(fixture.imageId(), "restore", fixture.userId());
                else service.hidePost(fixture.imageId(), "hide", fixture.userId());
                follower.set(executor.submit(() -> delete(fixture, started)));
                await(started);
                assertThatThrownBy(() -> follower.get().get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            });

            assertThat(follower.get().get(5, TimeUnit.SECONDS)).isNull();
            assertRemainingPhoto(fixture);
            assertThat(outbox.count()).isEqualTo(2L);
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable delete(Fixture fixture, CountDownLatch started) {
        started.countDown();
        try {
            service.deletePost(fixture.imageId(), fixture.userId());
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private void assertRemainingPhoto(Fixture fixture) {
        assertThat(images.existsById(fixture.imageId())).isFalse();
        assertThat(places.findById(fixture.placeId()).orElseThrow().currentPhotoCount()).isEqualTo(1L);
        assertThat(images.countByMapPlace_IdAndVisibilityStatus(fixture.placeId(), MapImageVisibilityStatus.ACTIVE))
                .isEqualTo(1L);
    }

    private Fixture fixture() {
        return new TransactionTemplate(transactions).execute(status -> {
            User user = users.saveAndFlush(User.builder().username("delete-owner")
                    .email("delete-owner@example.invalid").password("unused").birthYear(1995)
                    .language("ko").country("KR").role(UserRole.USER).build());
            MapPlace place = places.saveAndFlush(place("source", 2L));
            MapImage image = images.saveAndFlush(image(user, place, "target"));
            images.saveAndFlush(image(user, place, "remaining"));
            PostReport report = reports.saveAndFlush(PostReport.builder().reporterUserId(user.getId())
                    .reporterUsername(user.getUsername()).reportedUserId(user.getId())
                    .reportedImageId(image.getId()).reportedImageUrl(image.getImageUrl())
                    .mapImage(image).reason("test").build());
            return new Fixture(user.getId(), place.getId(), image.getId(), report.getId());
        });
    }

    private MapPlace place(String name, long photoCount) {
        return MapPlace.builder().name(name).address("서울시 중구").latitude(37.5).longitude(127.0)
                .registrant("test").photoCount(photoCount).build();
    }

    private MapImage image(User user, MapPlace place, String key) {
        return MapImage.builder().mapPlace(place).userId(user.getId()).username(user.getUsername())
                .title(key).imageUrl("https://example.invalid/" + key).s3Key(key)
                .thumbnailS3Key(key + "-thumbnail").build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("경합 동기화 시간 초과");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }

    private record Fixture(Long userId, Long placeId, Long imageId, Long reportId) {
    }
}
