package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamInvitationResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamInviteRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantTeamMemberResponse;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitation;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitationStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceInvitationRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMemberRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
class MerchantTeamInvitationPostgreSqlIntegrationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    ).withDatabaseName("pingdom").withUsername("pingdom").withPassword("pingdom");

    @Autowired private UserRepository userRepository;
    @Autowired private MapPlaceRepository placeRepository;
    @Autowired private MerchantOwnerProfileRepository profileRepository;
    @Autowired private MerchantOwnerPlaceRepository ownerPlaceRepository;
    @Autowired private MerchantPlaceMemberRepository memberRepository;
    @Autowired private MerchantPlaceInvitationRepository invitationRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private TransactionTemplate transaction;
    private MerchantTeamService service;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE users, map_place RESTART IDENTITY CASCADE");
        transaction = new TransactionTemplate(transactionManager);
        service = serviceUsing(invitationRepository);
    }

    // 외부 테스트 트랜잭션 없이 서비스의 실패/성공 트랜잭션을 종료한 뒤 저장 상태를 재조회합니다.
    @Test
    void reinvitesAfterExpiredAcceptanceHasRolledBack() {
        Fixture fixture = fixture(NOW.minusSeconds(1));

        assertExpiredAcceptance(fixture);
        assertThat(status(fixture.invitationId())).isEqualTo(MerchantPlaceInvitationStatus.PENDING);

        MerchantTeamInvitationResponse replacement = invite(fixture);

        assertReplacement(fixture, replacement);
        assertExpiredAcceptance(fixture);
        assertThat(status(replacement.id())).isEqualTo(MerchantPlaceInvitationStatus.PENDING);
    }

    @Test
    void reinvitesAtExpirationWithoutFirstAccepting() {
        Fixture fixture = fixture(NOW);

        MerchantTeamInvitationResponse replacement = invite(fixture);

        assertReplacement(fixture, replacement);
    }

    @Test
    void rollsBackExpirationWhenNewInvitationCannotBeSaved() {
        Fixture fixture = fixture(NOW.minusSeconds(1));
        MerchantPlaceInvitationRepository failingRepository = mock(
                MerchantPlaceInvitationRepository.class, delegatesTo(invitationRepository));
        doThrow(new DataIntegrityViolationException("new invitation storage failure"))
                .when(failingRepository).save(any(MerchantPlaceInvitation.class));
        MerchantTeamService failingService = serviceUsing(failingRepository);

        assertThatThrownBy(() -> inTransaction(() -> failingService.invite(
                fixture.ownerId(), fixture.placeId(), request(fixture))))
                .isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(MerchantOwnerErrorCode.MERCHANT_TEAM_INVITATION_ALREADY_EXISTS));

        assertThat(status(fixture.invitationId())).isEqualTo(MerchantPlaceInvitationStatus.PENDING);
        assertThat(invitationRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentReinvitationsLeaveOnlyOnePendingInvitation() throws Exception {
        Fixture fixture = fixture(NOW.minusSeconds(1));

        List<String> outcomes = together(() -> inviteOutcome(fixture), () -> inviteOutcome(fixture));

        assertThat(outcomes).containsExactlyInAnyOrder("created", "duplicate");
        assertThat(status(fixture.invitationId())).isEqualTo(MerchantPlaceInvitationStatus.EXPIRED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM merchant_place_invitation WHERE status = 'PENDING'", Long.class)).isEqualTo(1);
        assertThat(invitationRepository.count()).isEqualTo(2);
    }

    @Test
    void concurrentOldAcceptanceAndReinvitationKeepReplacementPending() throws Exception {
        Fixture fixture = fixture(NOW.minusSeconds(1));

        List<String> outcomes = together(() -> inviteOutcome(fixture), () -> {
            assertExpiredAcceptance(fixture);
            return "expired";
        });

        assertThat(outcomes).containsExactlyInAnyOrder("created", "expired");
        assertThat(status(fixture.invitationId())).isEqualTo(MerchantPlaceInvitationStatus.EXPIRED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM merchant_place_invitation WHERE status = 'PENDING'", Long.class)).isEqualTo(1);
        assertThat(memberRepository.count()).isZero();
    }

    @Test
    void replacementReactivatesRevokedMemberAndRepeatedAcceptanceIsIdempotent() {
        Fixture fixture = fixture(NOW.minusSeconds(1));
        Long memberId = inTransaction(() -> {
            MerchantPlaceMember member = MerchantPlaceMember.create(
                    fixture.placeId(), fixture.inviteeId(), MerchantPlaceMemberRole.MANAGER, fixture.ownerId(), NOW.minusDays(2));
            member.revoke(NOW.minusDays(1));
            return memberRepository.saveAndFlush(member).getId();
        });
        MerchantTeamInvitationResponse replacement = invite(fixture);

        MerchantTeamMemberResponse first = inTransaction(() -> service.acceptInvitation(fixture.inviteeId(), replacement.id()));
        MerchantTeamMemberResponse repeated = inTransaction(() -> service.acceptInvitation(fixture.inviteeId(), replacement.id()));

        assertThat(first.id()).isEqualTo(memberId);
        assertThat(repeated.id()).isEqualTo(memberId);
        assertThat(inTransaction(() -> memberRepository.findById(memberId).orElseThrow().getStatus()))
                .isEqualTo(MerchantPlaceMemberStatus.ACTIVE);
        assertThat(status(replacement.id())).isEqualTo(MerchantPlaceInvitationStatus.ACCEPTED);
        assertThat(memberRepository.count()).isEqualTo(1);
    }

    private Fixture fixture(LocalDateTime expiresAt) {
        return inTransaction(() -> {
            User owner = createUser("owner", UserRole.MERCHANT_OWNER);
            User invitee = createUser("invitee", UserRole.USER);
            profileRepository.saveAndFlush(MerchantOwnerProfile.pending(
                    owner.getId(), "business", "owner", null, "owner@example.com", "010-1111-2222", NOW.minusDays(1)));
            MapPlace place = placeRepository.saveAndFlush(MapPlace.builder()
                    .name("invitation test").address("test address").latitude(35.1).longitude(128.1)
                    .userId(owner.getId()).registrant("merchant").build());
            ownerPlaceRepository.saveAndFlush(MerchantOwnerPlace.builder()
                    .placeId(place.getId()).merchantOwnerUserId(owner.getId()).createdAt(NOW.minusDays(1)).build());
            MerchantPlaceInvitation invitation = invitationRepository.saveAndFlush(MerchantPlaceInvitation.pending(
                    place.getId(), invitee.getId(), owner.getId(), MerchantPlaceMemberRole.STAFF, expiresAt, NOW.minusDays(1)));
            return new Fixture(owner.getId(), invitee.getId(), place.getId(), invitation.getId());
        });
    }

    private User createUser(String name, UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .username(name).email(name + "@example.com").password("password")
                .birthYear(1990).language("ko").country("KR").role(role).build());
    }

    private MerchantTeamService serviceUsing(MerchantPlaceInvitationRepository invitations) {
        MerchantPlaceCapabilityPolicy policy = new MerchantPlaceCapabilityPolicy(
                memberRepository, ownerPlaceRepository, userRepository, CLOCK);
        return new MerchantTeamService(memberRepository, invitations, ownerPlaceRepository, userRepository, policy, CLOCK);
    }

    private MerchantTeamInviteRequest request(Fixture fixture) {
        return new MerchantTeamInviteRequest(fixture.inviteeId(), MerchantPlaceMemberRole.STAFF, null);
    }

    private MerchantTeamInvitationResponse invite(Fixture fixture) {
        return inTransaction(() -> service.invite(fixture.ownerId(), fixture.placeId(), request(fixture)));
    }

    private String inviteOutcome(Fixture fixture) {
        try {
            invite(fixture);
            return "created";
        } catch (MerchantOwnerException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(MerchantOwnerErrorCode.MERCHANT_TEAM_INVITATION_ALREADY_EXISTS);
            return "duplicate";
        }
    }

    private void assertExpiredAcceptance(Fixture fixture) {
        assertThatThrownBy(() -> inTransaction(() -> service.acceptInvitation(fixture.inviteeId(), fixture.invitationId())))
                .isInstanceOfSatisfying(MerchantOwnerException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MerchantOwnerErrorCode.MERCHANT_TEAM_INVITATION_EXPIRED));
    }

    private void assertReplacement(Fixture fixture, MerchantTeamInvitationResponse replacement) {
        assertThat(replacement.id()).isNotEqualTo(fixture.invitationId());
        assertThat(status(fixture.invitationId())).isEqualTo(MerchantPlaceInvitationStatus.EXPIRED);
        assertThat(status(replacement.id())).isEqualTo(MerchantPlaceInvitationStatus.PENDING);
        assertThat(replacement.expiresAt()).isEqualTo(NOW.plusDays(7));
        assertThat(invitationRepository.count()).isEqualTo(2);
    }

    private MerchantPlaceInvitationStatus status(Long invitationId) {
        return inTransaction(() -> invitationRepository.findById(invitationId).orElseThrow().getStatus());
    }

    private <T> T inTransaction(Supplier<T> action) {
        return transaction.execute(status -> action.get());
    }

    private <T> List<T> together(Supplier<T> first, Supplier<T> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<T> a = executor.submit(() -> afterStart(ready, start, first));
            Future<T> b = executor.submit(() -> afterStart(ready, start, second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private <T> T afterStart(CountDownLatch ready, CountDownLatch start, Supplier<T> action) {
        ready.countDown();
        try {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return action.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private record Fixture(Long ownerId, Long inviteeId, Long placeId, Long invitationId) {
    }
}
