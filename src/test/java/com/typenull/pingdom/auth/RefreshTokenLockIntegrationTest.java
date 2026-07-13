package com.typenull.pingdom.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typenull.pingdom.identity.domain.User;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
class RefreshTokenLockIntegrationTest extends AuthRegressionIntegrationTestSupport {

    private static final DockerImageName POSTGIS_IMAGE = DockerImageName
            .parse("postgis/postgis:16-3.4")
            .asCompatibleSubstituteFor("postgres");

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGIS_IMAGE)
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    static {
        postgres.start();
        ensureRequiredExtensions();
    }

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @AfterAll
    static void stopPostgres() {
        postgres.stop();
    }

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void refreshTokenStateLockSerializesConcurrentUpdates() throws Exception {
        User user = createUser("refreshTokenLockUser");
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstLock = new CountDownLatch(1);
        CountDownLatch secondAttempted = new CountDownLatch(1);
        CountDownLatch secondLockAcquired = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                userRepository.findByIdForUpdate(user.getId()).orElseThrow();
                firstLockAcquired.countDown();
                await(releaseFirstLock);
            }));
            assertTrue(firstLockAcquired.await(3, TimeUnit.SECONDS));

            Future<?> second = executor.submit(() -> {
                secondAttempted.countDown();
                transactionTemplate.executeWithoutResult(status -> {
                    userRepository.findByIdForUpdate(user.getId()).orElseThrow();
                    secondLockAcquired.countDown();
                });
            });
            assertTrue(secondAttempted.await(3, TimeUnit.SECONDS));
            assertFalse(secondLockAcquired.await(300, TimeUnit.MILLISECONDS));

            releaseFirstLock.countDown();
            first.get(3, TimeUnit.SECONDS);
            second.get(3, TimeUnit.SECONDS);
            assertTrue(secondLockAcquired.await(1, TimeUnit.SECONDS));
        } finally {
            releaseFirstLock.countDown();
            executor.shutdownNow();
        }
    }

    private static void ensureRequiredExtensions() {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS postgis");
        } catch (Exception exception) {
            throw new IllegalStateException("PostGIS 테스트 데이터베이스 준비에 실패했습니다.", exception);
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("행 잠금 해제 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("행 잠금 대기 중 인터럽트가 발생했습니다.", exception);
        }
    }
}
