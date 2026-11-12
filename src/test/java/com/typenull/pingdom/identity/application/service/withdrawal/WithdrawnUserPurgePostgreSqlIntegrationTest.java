package com.typenull.pingdom.identity.application.service.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.typenull.pingdom.identity.domain.PasswordResetToken;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserPurgeRetry.Reason;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.PasswordResetTokenRepository;
import com.typenull.pingdom.identity.domain.repository.UserPurgeRetryRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.privacy.application.PrivacyProcessingOutboxPublisher;
import com.typenull.pingdom.privacy.event.PrivacyProcessingBulkEvent;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cloud.aws.s3.enabled=false",
        "management.health.redis.enabled=false",
        "fcm.enabled=false",
        "outbox.enabled=false",
        "user.withdrawal.cleanup-enabled=false",
        "travel.data-retention.cleanup-enabled=false",
        "user.withdrawal.cleanup-batch-size=1",
        "user.withdrawal.max-cleanup-batches=1"
})
class WithdrawnUserPurgePostgreSqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 0, 0);

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private UserRepository users;
    @Autowired private UserPurgeRetryRepository retries;
    @Autowired private PasswordResetTokenRepository tokens;
    @Autowired private MapPlaceRepository places;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WithdrawnUserPurgeTransactionService transactions;
    @Autowired private WithdrawnUserPurgeService coordinator;
    @SpyBean private PrivacyProcessingOutboxPublisher publisher;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> placeIds = new ArrayList<>();

    @AfterEach
    void cleanFixtures() {
        for (Long id : userIds) {
            jdbc.update("DELETE FROM place_menu WHERE merchant_owner_user_id = ?", id);
            jdbc.update("DELETE FROM location_analysis_report WHERE owner_user_id = ?", id);
            jdbc.update("DELETE FROM merchant_owner_profile WHERE user_id = ?", id);
            jdbc.update("DELETE FROM password_reset_token WHERE user_id = ?", id);
            jdbc.update("DELETE FROM oauth_accounts WHERE user_id = ?", id);
            jdbc.update("DELETE FROM user_purge_retry WHERE user_id = ?", id);
            users.deleteById(id);
        }
        for (Long id : placeIds) {
            places.deleteById(id);
        }
    }

    @Test
    void deletesResetTokensAndOAuthBeforeUserAndCommitsOneDeletedOutbox() {
        User user = expired();
        tokens.saveAndFlush(PasswordResetToken.create(user, UUID.randomUUID().toString(), NOW.plusDays(1), NOW));
        jdbc.update("INSERT INTO oauth_accounts (provider, provider_id, user_id) VALUES ('GOOGLE', ?, ?)",
                UUID.randomUUID().toString(), user.getId());
        MapPlace place = place(user.getId());

        assertThat(transactions.purge(user.getId(), NOW.minusDays(30), NOW))
                .isEqualTo(WithdrawnUserPurgeTransactionService.Result.DELETED);
        assertThat(transactions.purge(user.getId(), NOW.minusDays(30), NOW))
                .isEqualTo(WithdrawnUserPurgeTransactionService.Result.SKIPPED);

        assertThat(users.existsById(user.getId())).isFalse();
        assertThat(count("password_reset_token", "user_id", user.getId())).isZero();
        assertThat(count("oauth_accounts", "user_id", user.getId())).isZero();
        assertThat(places.findById(place.getId()).orElseThrow().getUserId()).isNull();
        assertThat(deletedEvents(user.getId())).isEqualTo(1);
    }

    @Test
    void retainsMenuOwnerAndDoesNotLetTheSamePrefixStarveAnUnattemptedUserAfterRetryIsDue() {
        User held = expired();
        MapPlace place = place(held.getId());
        jdbc.update("""
                INSERT INTO place_menu
                  (place_id, merchant_owner_user_id, name, price_amount, currency, status,
                   display_order, created_at, updated_at)
                VALUES (?, ?, '보존 메뉴', 1000, 'KRW', 'INACTIVE', 0, ?, ?)
                """, place.getId(), held.getId(), NOW, NOW);
        User next = expired();

        assertThat(coordinator.purgeExpiredUsers(NOW)).isZero();
        assertThat(retries.findById(held.getId()).orElseThrow().getReason()).isEqualTo(Reason.RETAINED_REFERENCE);
        assertThat(coordinator.purgeExpiredUsers(NOW.plusDays(1))).isEqualTo(1);

        assertThat(users.existsById(held.getId())).isTrue();
        assertThat(count("place_menu", "merchant_owner_user_id", held.getId())).isEqualTo(1);
        assertThat(users.existsById(next.getId())).isFalse();
        assertThat(deletedEvents(held.getId())).isZero();
    }

    @Test
    void retainsOwnerReportThatHasNoPhysicalUserForeignKey() {
        User user = expired();
        jdbc.update("""
                INSERT INTO location_analysis_report
                  (report_id, report_name, category, region, email, privacy_consent, published_date,
                   analysis_basis_date, html_content, pdf_content, created_at, updated_at, owner_user_id)
                VALUES (?, '보존 보고서', '카페', '서울', 'contact@example.com', true,
                        CURRENT_DATE, CURRENT_DATE, '<html>보존</html>', decode('01', 'hex'), ?, ?, ?)
                """, UUID.randomUUID().toString(), NOW, NOW, user.getId());

        assertThat(transactions.purge(user.getId(), NOW.minusDays(30), NOW))
                .isEqualTo(WithdrawnUserPurgeTransactionService.Result.DEFERRED);
        assertThat(users.existsById(user.getId())).isTrue();
        assertThat(count("location_analysis_report", "owner_user_id", user.getId())).isEqualTo(1);
        assertThat(deletedEvents(user.getId())).isZero();
    }

    @Test
    void retainsCascadeProfileAndItsIndirectOwnerPlaceReference() {
        User user = expired();
        MapPlace place = place(user.getId());
        jdbc.update("""
                INSERT INTO merchant_owner_profile
                  (user_id, business_name, display_name, contact_email, contact_phone, status, created_at, updated_at)
                VALUES (?, '익명 사업자', '탈퇴 사용자', 'withdrawn@example.com', '000', 'REVOKED', ?, ?)
                """, user.getId(), NOW, NOW);
        jdbc.update("INSERT INTO merchant_owner_place (place_id, merchant_owner_user_id, created_at) VALUES (?, ?, ?)",
                place.getId(), user.getId(), NOW);

        assertThat(transactions.purge(user.getId(), NOW.minusDays(30), NOW))
                .isEqualTo(WithdrawnUserPurgeTransactionService.Result.DEFERRED);
        assertThat(users.existsById(user.getId())).isTrue();
        assertThat(count("merchant_owner_place", "merchant_owner_user_id", user.getId())).isEqualTo(1);
    }

    @Test
    void rollsBackAuthenticationAndContentCleanupWhenOutboxPublicationFails() {
        User user = expired();
        MapPlace place = place(user.getId());
        tokens.saveAndFlush(PasswordResetToken.create(user, UUID.randomUUID().toString(), NOW.plusDays(1), NOW));
        doThrow(new IllegalStateException("controlled outbox failure"))
                .when(publisher).publish(any(PrivacyProcessingBulkEvent.class));

        assertThat(coordinator.purgeExpiredUsers(NOW)).isZero();

        assertThat(users.existsById(user.getId())).isTrue();
        assertThat(tokens.count()).isEqualTo(1);
        assertThat(places.findById(place.getId()).orElseThrow().getUserId()).isEqualTo(user.getId());
        assertThat(retries.findById(user.getId()).orElseThrow().getReason()).isEqualTo(Reason.PROCESSING_FAILED);
        assertThat(deletedEvents(user.getId())).isZero();
    }

    @Test
    void skipsWhileAnotherInstanceOwnsTheTransactionAdvisoryLock() throws Exception {
        User user = expired();
        try (Connection connection = postgres.createConnection(""); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("SELECT pg_advisory_xact_lock(27420260622)");
            assertThat(transactions.purge(user.getId(), NOW.minusDays(30), NOW))
                    .isEqualTo(WithdrawnUserPurgeTransactionService.Result.SKIPPED);
            connection.rollback();
        }
        assertThat(transactions.purge(user.getId(), NOW.minusDays(30), NOW))
                .isEqualTo(WithdrawnUserPurgeTransactionService.Result.DELETED);
        assertThat(deletedEvents(user.getId())).isEqualTo(1);
    }

    private User expired() {
        String key = "purge-" + UUID.randomUUID().toString().substring(0, 16);
        User user = users.saveAndFlush(User.builder().username(key).email(key + "@withdrawn.local")
                .password("invalidated").birthYear(1998).language("ko").country("KR")
                .status(UserStatus.WITHDRAWN).withdrawnAt(NOW.minusDays(31)).build());
        userIds.add(user.getId());
        return user;
    }

    private MapPlace place(Long userId) {
        MapPlace place = places.saveAndFlush(MapPlace.builder().name("삭제 보존 장소").address("주소")
                .latitude(37.5).longitude(127.0).userId(userId).registrant(User.WITHDRAWN_DISPLAY_NAME).build());
        placeIds.add(place.getId());
        return place;
    }

    private long count(String table, String column, Long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Long.class, id);
    }

    private long deletedEvents(Long id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_type = 'PRIVACY:DELETED' AND aggregate_id = ?",
                Long.class, id.toString());
    }
}
