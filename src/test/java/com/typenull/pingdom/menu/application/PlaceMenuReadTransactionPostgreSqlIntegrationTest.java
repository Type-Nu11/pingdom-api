package com.typenull.pingdom.menu.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapabilityPolicy;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.menu.api.dto.PlaceMenuResponse;
import com.typenull.pingdom.menu.domain.MenuCurrency;
import com.typenull.pingdom.menu.domain.PlaceMenu;
import com.typenull.pingdom.menu.infrastructure.PlaceMenuRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
class PlaceMenuReadTransactionPostgreSqlIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    ).withDatabaseName("pingdom").withUsername("pingdom").withPassword("pingdom");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private PlaceMenuService service;
    @Autowired private PlaceMenuRepository menus;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private MerchantPlaceCapabilityPolicy capabilityPolicy;

    private final AtomicReference<String> transactionReadOnly = new AtomicReference<>();
    private Long ownerId;
    private Long placeId;
    private Long menuId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        ownerId = users.saveAndFlush(User.builder().username("menu-read-" + suffix)
                .email("menu-read-" + suffix + "@example.invalid").password("unused").birthYear(1995)
                .language("ko").country("KR").role(UserRole.USER).build()).getId();
        placeId = jdbc.queryForObject("""
                INSERT INTO map_place (place_name, address, latitude, longitude, registrant, photo_count)
                VALUES ('읽기 검증 장소', '서울시 중구', 37.5, 127.0, 'test', 0) RETURNING map_place_id
                """, Long.class);
        menuId = menus.saveAndFlush(PlaceMenu.create(placeId, ownerId, "메뉴", null, 1000L,
                MenuCurrency.KRW, null, 0, LocalDateTime.now())).getId();

        // 실제 서비스 트랜잭션 안의 동일 DB 연결을 관찰하며 권한 정책 자체는 단위 테스트 범위로 둡니다.
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            transactionReadOnly.set(jdbc.queryForObject("SHOW transaction_read_only", String.class));
            return null;
        }).when(capabilityPolicy).require(any(), any(), any());
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM place_menu WHERE place_menu_id = ?", menuId);
        jdbc.update("DELETE FROM map_place WHERE map_place_id = ?", placeId);
        jdbc.update("DELETE FROM users WHERE id = ?", ownerId);
    }

    @Test
    void readsOwnedMenuInActualReadOnlyTransaction() {
        // 바깥 테스트 트랜잭션 없이 서비스 프록시가 선언한 readOnly 트랜잭션을 시작해야 합니다.
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();

        PlaceMenuResponse response = service.getOwned(ownerId, placeId, menuId);

        assertThat(transactionReadOnly.get()).isEqualTo("on");
        assertThat(response.id()).isEqualTo(menuId);
        assertThat(response.placeId()).isEqualTo(placeId);
        assertThat(response.name()).isEqualTo("메뉴");
        assertThat(response.priceAmount()).isEqualTo(1000L);
    }

    @Test
    void readsOwnedMenuWhileAnotherTransactionHoldsItsWriteLock() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> writer = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            menus.findByIdForUpdate(menuId).orElseThrow();
            locked.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("메뉴 잠금 해제 신호 없음");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }));

        try {
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<PlaceMenuResponse> reader = executor.submit(() -> service.getOwned(ownerId, placeId, menuId));

            assertThat(reader.get(5, TimeUnit.SECONDS).id()).isEqualTo(menuId);
            assertThat(transactionReadOnly.get()).isEqualTo("on");
        } finally {
            release.countDown();
            try {
                writer.get(5, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
