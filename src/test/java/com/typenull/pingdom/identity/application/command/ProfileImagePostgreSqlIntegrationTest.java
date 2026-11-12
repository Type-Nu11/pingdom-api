package com.typenull.pingdom.identity.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.ProfileImageUploadIntent;
import com.typenull.pingdom.identity.domain.ProfileImageUploadIntent.State;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.ProfileImageUploadIntentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 실제 외부 TX의 commit/낙관적 충돌과 REQUIRES_NEW 원장 보존을 검증하는 별도 PostgreSQL 타깃. */
@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false", "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cloud.aws.s3.enabled=false", "management.health.redis.enabled=false",
        "fcm.enabled=false", "outbox.enabled=false", "identity.profile-image-storage.cleanup-enabled=false"
})
class ProfileImagePostgreSqlIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("pingdom").withUsername("pingdom").withPassword("pingdom");

    @Autowired private ChangeInfoService service;
    @Autowired private ProfileImageUploadService images;
    @Autowired private UserRepository users;
    @Autowired private ProfileImageUploadIntentRepository intents;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private S3ObjectStorage storage;
    @MockBean private ProfileImageFileValidator validator;
    @MockBean private Clock clock;
    private User user;
    private final MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", new byte[]{1});

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE users, profile_image_upload_intent, outbox_event RESTART IDENTITY CASCADE");
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(storage.publicUrl(anyString())).thenAnswer(invocation -> "https://images.example.invalid/" + invocation.getArgument(0));
        when(validator.validate(file)).thenReturn(new ProfileImageFileValidator.ValidatedProfileImage(new byte[]{1}, "image/png", "png"));
        user = users.saveAndFlush(User.builder().username("profile-owner").email("profile@example.invalid")
                .password("unused").birthYear(1995).language("ko").country("KR").profileImageUrl("https://legacy.invalid/old.png").build());
    }

    @Test
    void commitFailureRollsBackReferenceButPreservesRegisteredUpload() {
        when(storage.putAtKey(any(), anyString(), anyString(), any())).thenAnswer(invocation -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("commit rejected"); }
            });
            return null;
        });

        assertThrows(IllegalStateException.class, () -> service.changeProfileImage(file, user.getId()));

        assertThat(users.findById(user.getId()).orElseThrow().getProfileImageUrl()).isEqualTo(user.getProfileImageUrl());
        assertThat(intents.findAll()).singleElement().satisfies(intent -> assertThat(intent.getState()).isEqualTo(State.UPLOADING));
        verify(storage, never()).delete(anyString());
    }

    @Test
    void concurrentUserWriteRejectsStaleProfileAndKeepsUploadForDeferredCleanup() {
        when(storage.putAtKey(any(), anyString(), anyString(), any())).thenAnswer(invocation -> {
            TransactionTemplate concurrent = new TransactionTemplate(transactionManager);
            concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            concurrent.executeWithoutResult(status -> {
                User current = users.findById(user.getId()).orElseThrow();
                current.changeUsername("concurrent-name");
                users.saveAndFlush(current);
            });
            return null;
        });

        assertThrows(OptimisticLockingFailureException.class, () -> service.changeProfileImage(file, user.getId()));

        User stored = users.findById(user.getId()).orElseThrow();
        assertThat(stored.getProfileImageUrl()).isEqualTo(user.getProfileImageUrl());
        assertThat(stored.getUsername()).isEqualTo("concurrent-name");
        assertThat(intents.findAll()).singleElement().satisfies(intent -> assertThat(intent.getState()).isEqualTo(State.UPLOADING));
        verify(storage, never()).delete(anyString());
    }

    @Test
    void replacementCommitsBothStatesAndQueuesOnlyAfterFourteenDays() {
        String first = service.changeProfileImage(file, user.getId());
        String second = service.changeProfileImage(file, user.getId());
        ProfileImageUploadIntent old = intents.findAll().stream().filter(intent -> intent.getImageUrl().equals(first)).findFirst().orElseThrow();
        assertThat(old.getState()).isEqualTo(State.ORPHAN);
        assertThat(old.getOrphanedAt()).isEqualTo(NOW);
        assertThat(users.findById(user.getId()).orElseThrow().getProfileImageUrl()).isEqualTo(second);

        when(clock.instant()).thenReturn(NOW.plus(ProfileImageUploadIntent.ORPHAN_RETENTION).minusSeconds(1));
        assertThat(images.queueDueCleanup()).isZero();
        when(clock.instant()).thenReturn(NOW.plus(ProfileImageUploadIntent.ORPHAN_RETENTION));
        assertThat(images.queueDueCleanup()).isEqualTo(1);
        assertThat(intents.findById(old.getId()).orElseThrow().getDeleteAttempt()).isNotBlank();
        assertThat(intents.findAll()).filteredOn(intent -> intent.getImageUrl().equals(second))
                .singleElement().satisfies(intent -> assertThat(intent.getState()).isEqualTo(State.ATTACHED));
    }

    @Test
    void equalityIndexPreservesLongUtf8ExternalProfileUrl() {
        StringBuilder url = new StringBuilder("https://external.invalid/");
        while (url.length() < 2048) url.append((char) (0x4e00 + (url.length() * 7919) % 20000));
        user.changeProfileImageUrl(url.toString());
        users.saveAndFlush(user);

        assertThat(users.existsByProfileImageUrl(url.toString())).isTrue();
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_users_profile_image_url'", String.class))
                .contains("USING hash");
    }
}
