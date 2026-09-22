package com.typenull.pingdom.identity.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserBanType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("postgres-integration")
@Tag("postgres-smoke")
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
        "outbox.enabled=false"
})
class UserRepositoryPostgreSqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 12, 0);

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    /**
     * 저장소 쿼리를 실제 PostgreSQL에서 실행하도록 PostGIS 컨테이너 접속 정보를 등록.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private UserRepository userRepository;

    /**
     * 정지 기간 필터 결과가 이전 데이터의 영향을 받지 않도록 사용자 테이블을 비움.
     */
    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAllInBatch();
    }

    /**
     * 현재 정지 사용자 조회에서 기간 조건이 없거나 시작·종료 조건만 있을 때 각각의 결과와 최신 정지순 정렬을 검증.
     * PostgreSQL에서 선택적 null 조건과 전체 정지 수 집계가 함께 동작하는지도 확인.
     */
    @Test
    void filtersBannedUsersByOptionalPeriod() {
        User olderUser = bannedUser("older-banned-user", NOW.minusDays(10));
        User newerUser = bannedUser("newer-banned-user", NOW.minusDays(2));
        userRepository.saveAllAndFlush(List.of(olderUser, newerUser));

        assertThat(findCurrentlyBanned(false, null, false, null))
                .extracting(User::getUsername)
                .containsExactly("newer-banned-user", "older-banned-user");

        assertThat(findCurrentlyBanned(true, NOW.minusDays(5), false, null))
                .extracting(User::getUsername)
                .containsExactly("newer-banned-user");

        assertThat(findCurrentlyBanned(false, null, true, NOW.minusDays(5)))
                .extracting(User::getUsername)
                .containsExactly("older-banned-user");

        assertThat(userRepository.countCurrentlyBannedByType(
                UserBanType.PERMANENT,
                UserBanType.TEMPORARY,
                NOW,
                null
        ).total()).isEqualTo(2L);
    }

    /**
     * 시작·종료 필터 활성 여부와 값을 전달하고 정지 시각·ID 내림차순으로 첫 페이지를 조회.
     */
    private List<User> findCurrentlyBanned(
            boolean hasBannedFrom,
            LocalDateTime bannedFrom,
            boolean hasBannedTo,
            LocalDateTime bannedTo
    ) {
        return userRepository.findAllCurrentlyBanned(
                UserBanType.TEMPORARY,
                NOW,
                null,
                false,
                null,
                hasBannedFrom,
                bannedFrom,
                hasBannedTo,
                bannedTo,
                PageRequest.of(0, 20, Sort.by(
                        Sort.Order.desc("bannedAt"),
                        Sort.Order.desc("id")
                ))
        ).getContent();
    }

    /**
     * 기간 필터 경계를 비교할 수 있도록 주어진 시각에 정지된 사용자를 생성.
     */
    private User bannedUser(String username, LocalDateTime bannedAt) {
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .build();
        user.ban("테스트 밴", bannedAt);
        return user;
    }
}
