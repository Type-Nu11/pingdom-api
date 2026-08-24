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

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAllInBatch();
    }

    @Test
    void currentlyBannedQuerySupportsOptionalPeriodFiltersOnPostgreSql() {
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
