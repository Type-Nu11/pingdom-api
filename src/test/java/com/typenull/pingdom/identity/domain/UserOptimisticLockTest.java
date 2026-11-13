package com.typenull.pingdom.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Spring Context 없이 실제 User 매핑의 오래된 갱신 거절을 검증. PostgreSQL의 잠금·migration 검증은 별도. */
@Tag("integration")
class UserOptimisticLockTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);
    private static SessionFactory sessionFactory;

    @BeforeAll
    static void setUpPersistence() {
        sessionFactory = new Configuration()
                .addAnnotatedClass(User.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:" + UUID.randomUUID())
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();
    }

    @AfterAll
    static void closePersistence() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    /** A가 먼저 읽고 B가 보안 상태를 커밋한 뒤 A가 flush하는 순서를 독립 세션·트랜잭션으로 고정. */
    @ParameterizedTest(name = "{0} 이후 오래된 {1} 갱신을 거절한다")
    @MethodSource("overlappingChanges")
    void rejectsStaleUpdatesAfterSecurityStateCommits(SecurityChange securityChange, StaleChange staleChange) {
        Long userId = createUser();
        UserState expected;
        try (Session staleSession = sessionFactory.openSession()) {
            Transaction staleTransaction = staleSession.beginTransaction();
            try {
                User staleUser = staleSession.find(User.class, userId);
                try (Session securitySession = sessionFactory.openSession()) {
                    Transaction securityTransaction = securitySession.beginTransaction();
                    User currentUser = securitySession.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
                    securityChange.apply(currentUser);
                    securityTransaction.commit();
                    expected = UserState.from(currentUser);
                    assertThat(currentUser.getVersion()).isEqualTo(staleUser.getVersion() + 1);
                }

                staleChange.apply(staleUser);

                assertThatThrownBy(staleSession::flush).isInstanceOf(OptimisticLockException.class);
            } finally {
                staleTransaction.rollback();
            }
        }

        try (Session verificationSession = sessionFactory.openSession()) {
            assertThat(UserState.from(verificationSession.find(User.class, userId))).isEqualTo(expected);
        }
    }

    @Test
    void rejectsStaleDetachedMergeAfterWithdrawal() {
        Long userId = createUser();
        User detachedUser;
        try (Session readSession = sessionFactory.openSession()) {
            detachedUser = readSession.find(User.class, userId);
        }
        try (Session securitySession = sessionFactory.openSession()) {
            Transaction transaction = securitySession.beginTransaction();
            SecurityChange.WITHDRAWAL.apply(securitySession.find(User.class, userId));
            transaction.commit();
        }
        detachedUser.changeUsername("stale-name");

        try (Session writeSession = sessionFactory.openSession()) {
            Transaction transaction = writeSession.beginTransaction();
            try {
                assertThatThrownBy(() -> writeSession.merge(detachedUser)).isInstanceOf(OptimisticLockException.class);
            } finally {
                transaction.rollback();
            }
        }
        try (Session verificationSession = sessionFactory.openSession()) {
            User currentUser = verificationSession.find(User.class, userId);
            assertThat(currentUser.isWithdrawn()).isTrue();
            assertThat(currentUser.getUsername()).isEqualTo("withdrawn-" + userId);
            assertThat(currentUser.getVersion()).isEqualTo(1);
        }
    }

    @Test
    void preservesNormalProfileAndLockedRefreshWrites() {
        Long userId = createUser();
        try (Session profileSession = sessionFactory.openSession()) {
            Transaction transaction = profileSession.beginTransaction();
            User user = profileSession.find(User.class, userId);
            user.changeProfileImageUrl("https://example.com/profile.jpg");
            transaction.commit();
            assertThat(user.getVersion()).isEqualTo(1);
        }
        try (Session refreshSession = sessionFactory.openSession()) {
            Transaction transaction = refreshSession.beginTransaction();
            User user = refreshSession.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
            user.issueRefreshToken("rotated-refresh-token");
            transaction.commit();
            assertThat(user.getVersion()).isEqualTo(2);
        }

        try (Session verificationSession = sessionFactory.openSession()) {
            User currentUser = verificationSession.find(User.class, userId);
            assertThat(currentUser.getProfileImageUrl()).isEqualTo("https://example.com/profile.jpg");
            assertThat(currentUser.matchesRefreshToken("rotated-refresh-token")).isTrue();
            assertThat(currentUser.getVersion()).isEqualTo(2);
        }
    }

    private Long createUser() {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            String name = "user-" + UUID.randomUUID();
            User user = User.builder()
                    .username(name)
                    .email(name + "@example.com")
                    .password("initial-password")
                    .birthYear(1998)
                    .language("ko")
                    .country("KR")
                    .createdAt(NOW)
                    .updatedAt(NOW)
                    .build();
            user.issueRefreshToken("initial-refresh-token");
            user.issueEmailVerification("initial-code", NOW.plusMinutes(10));
            session.persist(user);
            transaction.commit();
            assertThat(user.getVersion()).isZero();
            return user.getId();
        }
    }

    private static Stream<Arguments> overlappingChanges() {
        return Arrays.stream(SecurityChange.values()).flatMap(security -> Arrays.stream(StaleChange.values())
                .map(stale -> Arguments.of(security, stale)));
    }

    private enum SecurityChange {
        WITHDRAWAL {
            @Override
            void apply(User user) {
                user.withdraw("withdrawn-" + user.getId(), "withdrawn-" + user.getId() + "@example.com",
                        "anonymized-password", NOW);
            }
        },
        BAN {
            @Override
            void apply(User user) {
                user.ban("account-security", NOW);
            }
        },
        REFRESH_ROTATION {
            @Override
            void apply(User user) {
                user.issueRefreshToken("rotated-refresh-token");
            }
        };

        abstract void apply(User user);
    }

    private enum StaleChange {
        USERNAME {
            @Override
            void apply(User user) {
                user.changeUsername("stale-username");
            }
        },
        PASSWORD {
            @Override
            void apply(User user) {
                user.changePassword("stale-password");
            }
        },
        PROFILE_IMAGE {
            @Override
            void apply(User user) {
                user.changeProfileImageUrl("https://example.com/stale.jpg");
            }
        },
        VERIFY_EMAIL {
            @Override
            void apply(User user) {
                user.verifyEmail();
            }
        },
        REISSUE_EMAIL {
            @Override
            void apply(User user) {
                user.issueEmailVerification("new-code", NOW.plusMinutes(20));
            }
        },
        MERCHANT_ROLE {
            @Override
            void apply(User user) {
                user.activateMerchantOwnerRole();
            }
        },
        TRAVEL_PURPOSES {
            @Override
            void apply(User user) {
                user.replaceTravelPurposes(Set.of(TravelPurpose.values()[0]));
            }
        },
        REPORT_COUNT {
            @Override
            void apply(User user) {
                user.increaseReportCount();
            }
        };

        abstract void apply(User user);
    }

    private record UserState(
            long version, String username, String email, String password, UserStatus status,
            boolean banned, String refreshToken, UserRole role, String profileImageUrl, boolean emailVerified,
            String verificationCode, LocalDateTime verificationExpiresAt
    ) {
        static UserState from(User user) {
            return new UserState(user.getVersion(), user.getUsername(), user.getEmail(), user.getPassword(),
                    user.getStatus(), user.isBanned(), user.getRefreshToken(), user.getRole(), user.getProfileImageUrl(),
                    user.isEmailVerified(), user.getEmailVerificationCode(), user.getEmailVerificationExpiresAt());
        }
    }
}
