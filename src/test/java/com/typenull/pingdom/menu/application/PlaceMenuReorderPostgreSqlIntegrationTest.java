package com.typenull.pingdom.menu.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapabilityPolicy;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.menu.api.dto.PlaceMenuOrderRequest;
import com.typenull.pingdom.menu.domain.MenuCurrency;
import com.typenull.pingdom.menu.domain.PlaceMenu;
import com.typenull.pingdom.menu.domain.PlaceMenuStatus;
import com.typenull.pingdom.menu.infrastructure.PlaceMenuRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cloud.aws.s3.enabled=false", "management.health.redis.enabled=false",
        "fcm.enabled=false", "outbox.enabled=false"
})
class PlaceMenuReorderPostgreSqlIntegrationTest {

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

    private Long ownerId;
    private Long placeId;
    private Long otherPlaceId;
    private List<Long> menuIds;
    private Long otherMenuId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        ownerId = users.saveAndFlush(User.builder().username("order-" + suffix)
                .email("order-" + suffix + "@example.invalid").password("unused").birthYear(1995)
                .language("ko").country("KR").role(UserRole.USER).build()).getId();
        placeId = insertPlace();
        otherPlaceId = insertPlace();
        menuIds = List.of(insertMenu(placeId, 0), insertMenu(placeId, 1), insertMenu(placeId, 2));
        otherMenuId = insertMenu(otherPlaceId, 0);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM place_menu WHERE place_id IN (?, ?)", placeId, otherPlaceId);
        jdbc.update("DELETE FROM map_place WHERE map_place_id IN (?, ?)", placeId, otherPlaceId);
        jdbc.update("DELETE FROM users WHERE id = ?", ownerId);
    }

    @Test
    void waitsAtLowestIdWithoutLockingRequestedHigherIdFirst() throws Exception {
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch lockSecond = new CountDownLatch(1);
        CountDownLatch reorderStarted = new CountDownLatch(1);
        AtomicInteger reorderPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var holder = executor.submit(() -> inTransaction(() -> {
                menus.findByIdForUpdate(menuIds.get(0)).orElseThrow();
                firstLocked.countDown();
                await(lockSecond);
                // 재정렬이 대상부터 잠그면 서로 상대 행을 기다리며 이 지점에서 교착한다.
                menus.findByIdForUpdate(menuIds.get(1)).orElseThrow();
                return null;
            }));
            await(firstLocked);
            var reorder = executor.submit(() -> inTransaction(() -> {
                markBackend(reorderPid, reorderStarted);
                return service.reorder(ownerId, placeId, menuIds.get(1), new PlaceMenuOrderRequest(0));
            }));
            await(reorderStarted);
            awaitBlocked(reorderPid.get());

            lockSecond.countDown();
            holder.get(10, TimeUnit.SECONDS);
            reorder.get(10, TimeUnit.SECONDS);

            assertOrder(List.of(menuIds.get(1), menuIds.get(0), menuIds.get(2)));
        } finally {
            lockSecond.countDown();
            shutdown(executor);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void appliesWaitingReorderToLatestCommittedOrder(boolean sameTarget) throws Exception {
        CountDownLatch firstReordered = new CountDownLatch(1);
        CountDownLatch commitFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger secondPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> inTransaction(() -> {
                service.reorder(ownerId, placeId, menuIds.get(2), new PlaceMenuOrderRequest(0));
                firstReordered.countDown();
                await(commitFirst);
                return null;
            }));
            await(firstReordered);
            Long secondTarget = sameTarget ? menuIds.get(2) : menuIds.get(0);
            var second = executor.submit(() -> inTransaction(() -> {
                markBackend(secondPid, secondStarted);
                return service.reorder(ownerId, placeId, secondTarget, new PlaceMenuOrderRequest(2));
            }));
            await(secondStarted);
            awaitBlocked(secondPid.get());

            commitFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            assertOrder(sameTarget ? menuIds : List.of(menuIds.get(2), menuIds.get(1), menuIds.get(0)));
        } finally {
            commitFirst.countDown();
            shutdown(executor);
        }
    }

    @Test
    void preservesStatusCommittedWhileReorderWaits() throws Exception {
        CountDownLatch statusChanged = new CountDownLatch(1);
        CountDownLatch commitStatus = new CountDownLatch(1);
        CountDownLatch reorderStarted = new CountDownLatch(1);
        AtomicInteger reorderPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var writer = executor.submit(() -> inTransaction(() -> {
                service.changeStatus(ownerId, placeId, menuIds.get(2), PlaceMenuStatus.SOLD_OUT);
                statusChanged.countDown();
                await(commitStatus);
                return null;
            }));
            await(statusChanged);
            var reorder = executor.submit(() -> inTransaction(() -> {
                markBackend(reorderPid, reorderStarted);
                return service.reorder(ownerId, placeId, menuIds.get(2), new PlaceMenuOrderRequest(0));
            }));
            await(reorderStarted);
            awaitBlocked(reorderPid.get());

            commitStatus.countDown();
            writer.get(10, TimeUnit.SECONDS);
            assertThat(reorder.get(10, TimeUnit.SECONDS).status()).isEqualTo(PlaceMenuStatus.SOLD_OUT);

            assertThat(menus.findById(menuIds.get(2)).orElseThrow().getStatus()).isEqualTo(PlaceMenuStatus.SOLD_OUT);
            assertOrder(List.of(menuIds.get(2), menuIds.get(0), menuIds.get(1)));
        } finally {
            commitStatus.countDown();
            shutdown(executor);
        }
    }

    @Test
    void doesNotSerializeReordersOfDifferentPlaces() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var holder = executor.submit(() -> inTransaction(() -> {
                menus.findAllByPlaceIdOrderByIdAscForUpdate(placeId);
                locked.countDown();
                await(release);
                return null;
            }));
            await(locked);
            var independent = executor.submit(() -> inTransaction(() ->
                    service.reorder(ownerId, otherPlaceId, otherMenuId, new PlaceMenuOrderRequest(0))));

            assertThat(independent.get(5, TimeUnit.SECONDS).id()).isEqualTo(otherMenuId);
            assertThat(holder.isDone()).isFalse();
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            shutdown(executor);
        }
    }

    private Long insertPlace() {
        return jdbc.queryForObject("""
                INSERT INTO map_place (place_name, address, latitude, longitude, registrant, photo_count)
                VALUES ('재정렬 검증 장소', '서울시 중구', 37.5, 127.0, 'test', 0) RETURNING map_place_id
                """, Long.class);
    }

    private Long insertMenu(Long targetPlaceId, int displayOrder) {
        return menus.saveAndFlush(PlaceMenu.create(targetPlaceId, ownerId, "메뉴", null, 1000L,
                MenuCurrency.KRW, null, displayOrder, LocalDateTime.now())).getId();
    }

    private void assertOrder(List<Long> expectedIds) {
        assertThat(jdbc.queryForList("""
                SELECT place_menu_id FROM place_menu WHERE place_id = ? ORDER BY display_order, place_menu_id
                """, Long.class, placeId)).containsExactlyElementsOf(expectedIds);
        assertThat(jdbc.queryForList("""
                SELECT display_order FROM place_menu WHERE place_id = ? ORDER BY display_order, place_menu_id
                """, Integer.class, placeId)).containsExactly(0, 1, 2);
    }

    private <T> T inTransaction(Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(12);
        return transaction.execute(status -> {
            jdbc.execute("SET LOCAL lock_timeout = '5s'");
            jdbc.execute("SET LOCAL statement_timeout = '10s'");
            return action.get();
        });
    }

    private void markBackend(AtomicInteger backendPid, CountDownLatch started) {
        backendPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
        started.countDown();
    }

    private void awaitBlocked(int backendPid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT cardinality(pg_blocking_pids(?)) > 0", Boolean.class, backendPid))) return;
            // 경합을 시작 신호만으로 추정하지 않고 실제 DB 잠금 대기 진입을 관측한다.
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            if (Thread.currentThread().isInterrupted()) throw new AssertionError("잠금 관측 대기 중 인터럽트");
        }
        throw new AssertionError("재정렬이 예상한 DB 잠금 대기에 진입하지 않음");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("트랜잭션 동기화 시간 초과");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
}
