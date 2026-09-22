package com.typenull.pingdom.community;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.community.application.CommunityPlaceViewService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
        "spring.docker.compose.enabled=false", "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.cloud.aws.s3.enabled=false",
        "management.health.redis.enabled=false", "fcm.enabled=false", "outbox.enabled=false"
})
class CommunityPlaceViewConcurrencyPostgreSqlIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    ).withDatabaseName("pingdom").withUsername("pingdom").withPassword("pingdom");

    /**
     * 동시 일일 조회 기록 삽입과 집계를 실제 PostgreSQL에서 확인하도록 컨테이너 접속 정보를 등록.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private CommunityPlaceViewService communityPlaceViewService;
    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * 동시 조회 테스트가 만든 일일 기록과 게시글·장소 연결 및 대상 데이터를 의존 순서대로 제거.
     */
    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM community_place_daily_view");
        jdbcTemplate.update("DELETE FROM community_post_place");
        jdbcTemplate.update("DELETE FROM community_post");
        jdbcTemplate.update("DELETE FROM map_place");
    }

    /**
     * 같은 사용자가 한 장소로 동시에 진입해도 두 작업이 성공하고 장소 조회수와 일일 기록 수가 각각 1인지 검증.
     */
    @Test
    void countsConcurrentUserViewOnce() throws Exception {
        Fixture fixture = fixture();

        List<? extends Result<?>> results = runConcurrently(
                () -> communityPlaceViewService.record(fixture.postId(), fixture.placeId(), 100L),
                () -> communityPlaceViewService.record(fixture.postId(), fixture.placeId(), 100L)
        );

        assertThat(results).allSatisfy(result -> assertThat(result.failure()).isNull());
        assertThat(count("SELECT community_view_count FROM map_place WHERE map_place_id = ?", fixture.placeId())).isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM community_place_daily_view WHERE map_place_id = ?", fixture.placeId())).isEqualTo(1L);
    }

    /**
     * 서로 다른 사용자의 동시 진입은 두 작업이 성공하고 장소 조회수가 2로 증가하는지 검증.
     */
    @Test
    void countsConcurrentDistinctUserViews() throws Exception {
        Fixture fixture = fixture();

        List<? extends Result<?>> results = runConcurrently(
                () -> communityPlaceViewService.record(fixture.postId(), fixture.placeId(), 100L),
                () -> communityPlaceViewService.record(fixture.postId(), fixture.placeId(), 200L)
        );

        assertThat(results).allSatisfy(result -> assertThat(result.failure()).isNull());
        assertThat(count("SELECT community_view_count FROM map_place WHERE map_place_id = ?", fixture.placeId())).isEqualTo(2L);
    }

    /**
     * DB에 게시글·장소와 연결 행을 직접 저장하고 동시 조회에 필요한 두 ID를 묶어 반환.
     */
    private Fixture fixture() {
        Long placeId = jdbcTemplate.queryForObject("""
                INSERT INTO map_place (place_name, address, latitude, longitude, registrant, photo_count)
                VALUES ('동시성 장소', '서울시 중구', 37.5, 127.0, 'test', 0) RETURNING map_place_id
                """, Long.class);
        Long postId = jdbcTemplate.queryForObject("""
                INSERT INTO community_post (category_id, title, content, user_id)
                VALUES ('PLACE', '동시성 게시글', '본문', 1) RETURNING community_post_id
                """, Long.class);
        jdbcTemplate.update("INSERT INTO community_post_place (community_post_id, map_place_id) VALUES (?, ?)", postId, placeId);
        return new Fixture(postId, placeId);
    }

    /**
     * 대상 장소 ID를 조건으로 집계 SQL을 실행해 최종 저장 수치를 읽음.
     */
    private long count(String sql, Long placeId) {
        return jdbcTemplate.queryForObject(sql, Long.class, placeId);
    }

    /**
     * 두 작업을 별도 스레드에서 같은 배리어로 시작하고 제한 시간 안에 결과를 수집한 뒤 실행기를 정리.
     */
    private <T> List<Result<T>> runConcurrently(Callable<T> first, Callable<T> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Result<T>>> futures = List.of(
                    executor.submit(() -> callAfterBarrier(barrier, first)),
                    executor.submit(() -> callAfterBarrier(barrier, second))
            );
            return List.of(futures.get(0).get(10, TimeUnit.SECONDS), futures.get(1).get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 두 호출이 준비될 때까지 대기한 뒤 실행 결과 또는 실패를 값으로 담아 두 작업을 함께 검증할 수 있게 함.
     */
    private <T> Result<T> callAfterBarrier(CyclicBarrier barrier, Callable<T> callable) throws Exception {
        barrier.await();
        try { return new Result<>(callable.call(), null); }
        catch (Throwable failure) { return new Result<>(null, failure); }
    }

    private record Fixture(long postId, long placeId) { }
    private record Result<T>(T value, Throwable failure) { }
}
