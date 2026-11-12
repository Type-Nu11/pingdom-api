package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPlaceUpdateRequest;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOperationalQualityStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.offer.domain.OfferStatus;
import com.typenull.pingdom.offer.domain.TouristOffer;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
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
class MerchantOwnerPlaceReplacementPostgreSqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 31, 3, 0);
    private static final LocalDateTime CREATED_AT = NOW.minusDays(10);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-31T03:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    ).withDatabaseName("pingdom").withUsername("pingdom").withPassword("pingdom");

    @Autowired private UserRepository userRepository;
    @Autowired private MerchantOwnerProfileRepository profileRepository;
    @Autowired private MerchantOwnerPlaceRepository ownerPlaceRepository;
    @Autowired private MapPlaceRepository mapPlaceRepository;
    @Autowired private TouristOfferRepository touristOfferRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private TransactionTemplate transaction;
    private AdminAuditLogService auditLogService;
    private MerchantOwnerAdminService service;

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
        auditLogService = mock(AdminAuditLogService.class);
        service = serviceUsing(ownerPlaceRepository);
    }

    static Stream<Arguments> replacements() {
        return Stream.of("same", "add", "remove")
                .flatMap(mode -> Stream.of(false, true).map(measured -> Arguments.of(mode, measured)));
    }

    // 테스트 트랜잭션의 자동 롤백에 가리지 않도록 실제 커밋 후 새 트랜잭션에서 읽음.
    @ParameterizedTest(name = "{0}, measured={1}")
    @MethodSource("replacements")
    void commitsOnlyTheRequestedDifferenceAndPreservesQuality(String mode, boolean measured) {
        Fixture fixture = fixture(measured);
        Snapshot retainedBefore = snapshot(fixture.retainedPlaceId());
        Snapshot removedBefore = snapshot(fixture.removedPlaceId());
        Set<Long> requested = switch (mode) {
            case "add" -> Set.of(fixture.retainedPlaceId(), fixture.removedPlaceId(), fixture.addedPlaceId());
            case "remove" -> Set.of(fixture.retainedPlaceId());
            default -> Set.of(fixture.retainedPlaceId(), fixture.removedPlaceId());
        };

        replace(fixture.ownerId(), requested);

        assertThat(inTransaction(() -> ownerPlaceRepository.findPlaceIdsByMerchantOwnerUserId(fixture.ownerId())))
                .containsExactlyElementsOf(requested.stream().sorted().toList());
        assertThat(snapshot(fixture.retainedPlaceId())).isEqualTo(retainedBefore);
        if (!mode.equals("remove")) {
            assertThat(snapshot(fixture.removedPlaceId())).isEqualTo(removedBefore);
        }
        if (mode.equals("add")) {
            Snapshot added = snapshot(fixture.addedPlaceId());
            assertThat(added.ownerId()).isEqualTo(fixture.ownerId());
            assertThat(added.createdAt()).isEqualTo(NOW);
            assertThat(added.quality()).isEqualTo(MerchantOperationalQualityStatus.UNMEASURED);
        }
        assertThat(inTransaction(() -> touristOfferRepository.findById(fixture.offerId()).orElseThrow().getStatus()))
                .isEqualTo(mode.equals("remove") ? OfferStatus.CLOSED : OfferStatus.PUBLISHED);
    }

    @Test
    void rollsBackMappingsAndOfferClosureWhenAuditFails() {
        Fixture fixture = fixture(true);
        Snapshot retainedBefore = snapshot(fixture.retainedPlaceId());
        Snapshot removedBefore = snapshot(fixture.removedPlaceId());
        when(auditLogService.record(any(), any(), any(), any(), anyString(), any(), any()))
                .thenThrow(new IllegalStateException("audit unavailable"));

        assertThatThrownBy(() -> replace(fixture.ownerId(), Set.of(fixture.retainedPlaceId(), fixture.addedPlaceId())))
                .isInstanceOf(IllegalStateException.class).hasMessage("audit unavailable");

        assertThat(snapshot(fixture.retainedPlaceId())).isEqualTo(retainedBefore);
        assertThat(snapshot(fixture.removedPlaceId())).isEqualTo(removedBefore);
        assertThat(inTransaction(() -> ownerPlaceRepository.existsById(fixture.addedPlaceId()))).isFalse();
        assertThat(inTransaction(() -> touristOfferRepository.findById(fixture.offerId()).orElseThrow().getStatus()))
                .isEqualTo(OfferStatus.PUBLISHED);
    }

    @Test
    void permitsOnlyOneOwnerWhenTwoReplacementsAddTheSamePlace() throws Exception {
        Fixture fixture = fixture(false);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> addAfterStart(
                    ready, start, fixture.ownerId(), fixture.addedPlaceId()
            ));
            Future<Boolean> second = executor.submit(() -> addAfterStart(
                    ready, start, fixture.otherOwnerId(), fixture.addedPlaceId()
            ));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
        assertThat(snapshot(fixture.addedPlaceId()).ownerId()).isIn(fixture.ownerId(), fixture.otherOwnerId());
    }

    @Test
    void keepsOwnershipTransferredUnderClaimLocksWhileReplacementWaits() throws Exception {
        Fixture fixture = fixture(true);
        CountDownLatch placeLocked = new CountDownLatch(1);
        CountDownLatch initialIdsRead = new CountDownLatch(1);
        MerchantOwnerPlaceRepository observedRepository = mock(MerchantOwnerPlaceRepository.class, delegatesTo(ownerPlaceRepository));
        doAnswer(invocation -> {
            List<Long> ids = ownerPlaceRepository.findPlaceIdsByMerchantOwnerUserId(fixture.ownerId());
            initialIdsRead.countDown();
            return ids;
        }).when(observedRepository).findPlaceIdsByMerchantOwnerUserId(fixture.ownerId());
        MerchantOwnerAdminService observedService = serviceUsing(observedRepository);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> transfer = executor.submit(() -> inTransaction(() -> {
                mapPlaceRepository.findByIdForUpdate(fixture.removedPlaceId()).orElseThrow();
                MerchantOwnerPlace mapping = ownerPlaceRepository.findByPlaceIdForUpdate(fixture.removedPlaceId()).orElseThrow();
                mapping.transferOwnership(fixture.otherOwnerId());
                placeLocked.countDown();
                await(initialIdsRead);
                return null;
            }));
            assertThat(placeLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> replacement = executor.submit(() -> inTransaction(() -> observedService.replacePlaces(
                    fixture.ownerId(), fixture.ownerId(),
                    new MerchantOwnerPlaceUpdateRequest(Set.of(fixture.retainedPlaceId()), "replace")
            )));
            transfer.get(10, TimeUnit.SECONDS);
            replacement.get(10, TimeUnit.SECONDS);
        } finally {
            initialIdsRead.countDown();
            executor.shutdownNow();
        }
        assertThat(snapshot(fixture.removedPlaceId()).ownerId()).isEqualTo(fixture.otherOwnerId());
        assertThat(inTransaction(() -> touristOfferRepository.findById(fixture.offerId()).orElseThrow().getStatus()))
                .isEqualTo(OfferStatus.PUBLISHED);
    }

    private MerchantOwnerAdminService serviceUsing(MerchantOwnerPlaceRepository mappings) {
        return new MerchantOwnerAdminService(
                userRepository, profileRepository, mappings, mapPlaceRepository, auditLogService,
                mock(UserAccessStatusService.class), touristOfferRepository, mock(ApplicationEventPublisher.class),
                CLOCK, mock(AdminRoleAuthorizationService.class), mock(PlaceRegistrationApplicationRepository.class)
        );
    }

    private Fixture fixture(boolean measured) {
        return inTransaction(() -> {
            Long ownerId = createOwner("owner");
            Long otherOwnerId = createOwner("other");
            Long retained = createPlace(ownerId, "retained");
            Long removed = createPlace(ownerId, "removed");
            Long added = createPlace(ownerId, "added");
            List<MerchantOwnerPlace> mappings = new ArrayList<>();
            for (Long placeId : List.of(retained, removed)) {
                MerchantOwnerPlace mapping = MerchantOwnerPlace.builder()
                        .placeId(placeId).merchantOwnerUserId(ownerId).createdAt(CREATED_AT).build();
                if (measured) {
                    mapping.updateOperationalQuality(MerchantOperationalQualityStatus.HEALTHY, 93, 4, 2, NOW.minusDays(1));
                }
                mappings.add(mapping);
            }
            ownerPlaceRepository.saveAllAndFlush(mappings);
            TouristOffer offer = TouristOffer.draft(
                    ownerId, removed, "offer", "description", "benefit", NOW.minusDays(1), NOW.plusDays(5), 10, 3, NOW
            );
            offer.publish(NOW);
            touristOfferRepository.saveAndFlush(offer);
            return new Fixture(ownerId, otherOwnerId, retained, removed, added, offer.getId());
        });
    }

    private Long createOwner(String name) {
        User user = userRepository.saveAndFlush(User.builder()
                .username(name).email(name + "@example.com").password("password")
                .birthYear(1990).language("ko").country("KR").role(UserRole.MERCHANT_OWNER).build());
        MerchantOwnerProfile profile = MerchantOwnerProfile.pending(
                user.getId(), "business", name, null, name + "@example.com", "010-1111-2222", CREATED_AT
        );
        profile.approve(user.getId(), CREATED_AT);
        profileRepository.saveAndFlush(profile);
        return user.getId();
    }

    private Long createPlace(Long ownerId, String name) {
        return mapPlaceRepository.saveAndFlush(MapPlace.builder()
                .name(name).address("test address").latitude(35.1).longitude(128.1)
                .userId(ownerId).registrant("merchant").build()).getId();
    }

    private void replace(Long ownerId, Set<Long> requested) {
        inTransaction(() -> service.replacePlaces(ownerId, ownerId, new MerchantOwnerPlaceUpdateRequest(requested, "replace")));
    }

    private boolean addAfterStart(CountDownLatch ready, CountDownLatch start, Long ownerId, Long placeId) {
        ready.countDown();
        await(start);
        try {
            replace(ownerId, Set.of(placeId));
            return true;
        } catch (MerchantOwnerException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(MerchantOwnerErrorCode.PLACE_ALREADY_ASSIGNED);
            return false;
        }
    }

    private Snapshot snapshot(Long placeId) {
        return inTransaction(() -> {
            MerchantOwnerPlace mapping = ownerPlaceRepository.findById(placeId).orElseThrow();
            return new Snapshot(
                    mapping.getMerchantOwnerUserId(), mapping.getOperationalQualityStatus(),
                    mapping.getReservationResponseRate(), mapping.getReservationCancellationRate(),
                    mapping.getNoShowRate(), mapping.getQualityEvaluatedAt(), mapping.getCreatedAt()
            );
        });
    }

    private <T> T inTransaction(Supplier<T> action) {
        return transaction.execute(status -> action.get());
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private record Fixture(Long ownerId, Long otherOwnerId, Long retainedPlaceId, Long removedPlaceId, Long addedPlaceId, Long offerId) {
    }

    private record Snapshot(
            Long ownerId, MerchantOperationalQualityStatus quality, Integer responseRate,
            Integer cancellationRate, Integer noShowRate, LocalDateTime evaluatedAt, LocalDateTime createdAt
    ) {
    }
}
