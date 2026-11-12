package com.typenull.pingdom.identity.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.typenull.pingdom.identity.api.dto.profile.ChangeUsernameRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandler;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cloud.aws.s3.enabled=false", "management.health.redis.enabled=false",
        "fcm.enabled=false", "outbox.enabled=false"
})
class UsernameChangePostgreSqlIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    ).withDatabaseName("pingdom").withUsername("pingdom").withPassword("pingdom");

    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    private TransactionTemplate transaction;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
        transaction = new TransactionTemplate(transactionManager);
    }

    @Test
    void allowsCurrentNameWithoutIncrementingUserVersion() {
        User user = users.saveAndFlush(user("unchanged-user"));

        transaction.executeWithoutResult(status -> service(users)
                .changeUsername(new ChangeUsernameRequest(user.getUsername()), user.getId()));

        User stored = users.findById(user.getId()).orElseThrow();
        assertThat(stored.getUsername()).isEqualTo(user.getUsername());
        assertThat(stored.getVersion()).isEqualTo(user.getVersion());
    }

    @Test
    void mapsConcurrentRenameAfterBothPrechecksAndRollsBackLoser() throws Exception {
        User first = users.saveAndFlush(user("first-user"));
        User second = users.saveAndFlush(user("second-user"));
        String target = "raced-username";
        CyclicBarrier bothPrechecked = new CyclicBarrier(2);
        UserRepository observed = mock(UserRepository.class, delegatesTo(users));
        doAnswer(invocation -> {
            boolean exists = users.existsByUsername(target);
            assertThat(exists).isFalse();
            bothPrechecked.await(10, TimeUnit.SECONDS);
            return exists;
        }).when(observed).existsByUsername(target);
        ChangeInfoService service = service(observed);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RenameResult> a = executor.submit(() -> rename(service, first.getId(), target));
            Future<RenameResult> b = executor.submit(() -> rename(service, second.getId(), target));
            List<RenameResult> results = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));

            assertThat(results).filteredOn(RenameResult::succeeded).hasSize(1);
            RenameResult loser = results.stream().filter(result -> !result.succeeded()).findFirst().orElseThrow();
            assertThat(loser.errorCode()).isEqualTo(UsersErrorCode.USERNAME_ALREADY_EXISTS);
            assertThat(users.findByUsername(target)).isPresent();
            User storedLoser = users.findById(loser.userId()).orElseThrow();
            assertThat(storedLoser.getUsername())
                    .isEqualTo(loser.userId().equals(first.getId()) ? first.getUsername() : second.getUsername());
            assertThat(storedLoser.getVersion()).isZero();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void mapsActualFlywayUsernameConstraintToSignupConflict() {
        users.saveAndFlush(user("duplicate-user"));
        User duplicate = user("duplicate-user", "different-email@example.invalid");
        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> transaction.executeWithoutResult(status -> users.saveAndFlush(duplicate)));

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler(mock(AuthMetrics.class))
                .handleDataIntegrityViolationException(failure);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("DUPLICATE_USERNAME");
    }

    private RenameResult rename(ChangeInfoService service, Long userId, String username) {
        try {
            transaction.executeWithoutResult(status -> service.changeUsername(new ChangeUsernameRequest(username), userId));
            return new RenameResult(userId, null);
        } catch (UsersException exception) {
            ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler(mock(AuthMetrics.class))
                    .handleDomainException(exception);
            assertThat(response.getStatusCode().value()).isEqualTo(409);
            assertThat(response.getBody().code()).isEqualTo("USERNAME_ALREADY_EXISTS");
            return new RenameResult(userId, (UsersErrorCode) exception.getErrorCode());
        }
    }

    private ChangeInfoService service(UserRepository repository) {
        return new ChangeInfoService(repository, mock(PasswordEncoder.class),
                mock(ProfileImageFileValidator.class), mock(S3ObjectStorage.class));
    }

    private User user(String username) {
        return user(username, username + "@example.invalid");
    }

    private User user(String username, String email) {
        return User.builder().username(username).email(email).password("unused")
                .birthYear(1995).language("ko").country("KR").build();
    }

    private record RenameResult(Long userId, UsersErrorCode errorCode) {
        boolean succeeded() {
            return errorCode == null;
        }
    }
}
