package com.typenull.pingdom.availability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.availability.domain.AvailabilityStatus;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.availability.domain.exception.AvailabilityErrorCode;
import com.typenull.pingdom.availability.domain.exception.AvailabilityException;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.NearbyReservablePlaceQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.NearbyReservablePlaceQueryRepository.NearbyReservablePlaceProjection;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 공통 점주 자격을 같은 DB 상태로 비교하며 모든 저장소 쿼리와 reserve를 실제 호출한다. */
@Tag("postgres-integration")
@Testcontainers
@Transactional
@Import(ReservableMerchantEligibilityPostgreSqlIntegrationTest.FixedClockConfiguration.class)
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false", "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cloud.aws.s3.enabled=false", "management.health.redis.enabled=false",
        "fcm.enabled=false", "outbox.enabled=false"
})
class ReservableMerchantEligibilityPostgreSqlIntegrationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

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

    @Autowired private AvailabilityAccessPolicy policy;
    @Autowired private PlaceAvailabilityRepository slots;
    @Autowired private NearbyReservablePlaceQueryRepository nearby;
    @Autowired private PlaceAvailabilityService availabilityService;
    @Autowired private UserRepository users;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;

    @ParameterizedTest(name = "공통 자격: {0}")
    @EnumSource(MerchantCase.class)
    void agreesOnCurrentMerchantEligibilityAcrossAllPaths(MerchantCase scenario) {
        Fixture control = fixture();
        Fixture target = fixture();
        long laterSlotId = slot(target.ownerId(), target.placeId(), NOW.plusHours(4), NOW.plusHours(5));
        changeMerchantState(scenario, target);
        flushAndClear();

        if (scenario.allowed) {
            assertThatCode(() -> policy.requireOwnedPlace(target.ownerId(), target.placeId(), NOW))
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> policy.requireOwnedPlace(target.ownerId(), target.placeId(), NOW))
                    .isInstanceOfSatisfying(AvailabilityException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(AvailabilityErrorCode.PLACE_NOT_OWNED));
        }
        assertPublicAndRecommendation(control, target,
                scenario.allowed ? List.of(target.slotId(), laterSlotId) : List.of());
        assertThat(slots.findReservableByIdForUpdate(target.slotId(), NOW).isPresent())
                .isEqualTo(scenario.allowed);
        assertNearby(control, target, scenario.allowed);

        // 거절된 명령이 트랜잭션을 rollback-only로 바꾸기 전에 모든 조회 경로를 검증한다.
        if (scenario.allowed) {
            assertThat(availabilityService.reserve(target.slotId(), 1).getRemainingCapacity()).isEqualTo(9);
            em.flush();
        } else {
            assertReserveRejected(target, AvailabilityErrorCode.AVAILABILITY_NOT_FOUND);
        }
    }

    @ParameterizedTest(name = "슬롯 추가 조건: {0}")
    @EnumSource(SlotCase.class)
    void preservesSlotGuardsOutsideMerchantEligibility(SlotCase scenario) {
        Fixture control = fixture();
        Fixture target = fixture();
        switch (scenario) {
            case STARTED -> jdbc.update("UPDATE place_availability SET starts_at=?, ends_at=? WHERE id=?",
                    NOW.minusHours(1), NOW.plusHours(1), target.slotId());
            case ENDED -> jdbc.update("UPDATE place_availability SET starts_at=?, ends_at=? WHERE id=?",
                    NOW.minusHours(1), NOW, target.slotId());
            case INACTIVE -> jdbc.update("UPDATE place_availability SET status='INACTIVE' WHERE id=?", target.slotId());
            case EXHAUSTED -> jdbc.update("UPDATE place_availability SET remaining_capacity=0 WHERE id=?", target.slotId());
            case RETIRED -> jdbc.update("UPDATE place_availability SET booking_retired=TRUE WHERE id=?", target.slotId());
        }
        flushAndClear();

        assertThatCode(() -> policy.requireOwnedPlace(target.ownerId(), target.placeId(), NOW))
                .doesNotThrowAnyException();
        assertPublicAndRecommendation(control, target,
                scenario == SlotCase.STARTED ? List.of(target.slotId()) : List.of());
        assertThat(slots.findReservableByIdForUpdate(target.slotId(), NOW).isPresent())
                .isEqualTo(scenario != SlotCase.RETIRED);
        assertNearby(control, target, false);
        assertReserveRejected(target, scenario == SlotCase.RETIRED
                ? AvailabilityErrorCode.AVAILABILITY_NOT_FOUND : AvailabilityErrorCode.AVAILABILITY_CAPACITY_EXCEEDED);
    }

    @ParameterizedTest(name = "주변 검색 추가 조건: {0}")
    @EnumSource(PlaceCase.class)
    void preservesNearbyPlaceGuardsWithoutChangingPublicOrReservePaths(PlaceCase scenario) {
        Fixture control = fixture();
        Fixture target = fixture();
        switch (scenario) {
            case HIDDEN -> jdbc.update("UPDATE map_place SET discovery_status='HIDDEN' WHERE map_place_id=?", target.placeId());
            case CLOSED -> jdbc.update("UPDATE map_place SET operating_status='PERMANENTLY_CLOSED' WHERE map_place_id=?", target.placeId());
            case NO_LOCATION -> jdbc.update("UPDATE map_place SET location=NULL WHERE map_place_id=?", target.placeId());
            case OUTSIDE_RADIUS -> jdbc.update("""
                    UPDATE map_place SET latitude=38.0, location=ST_SetSRID(ST_MakePoint(127.0, 38.0), 4326)
                    WHERE map_place_id=?
                    """, target.placeId());
        }
        flushAndClear();

        assertThatCode(() -> policy.requireOwnedPlace(target.ownerId(), target.placeId(), NOW))
                .doesNotThrowAnyException();
        assertPublicAndRecommendation(control, target, List.of(target.slotId()));
        assertThat(slots.findReservableByIdForUpdate(target.slotId(), NOW)).isPresent();
        assertNearby(control, target, false);
        assertThat(availabilityService.reserve(target.slotId(), 1).getRemainingCapacity()).isEqualTo(9);
        em.flush();
    }

    private void assertPublicAndRecommendation(Fixture control, Fixture target, List<Long> expectedSlots) {
        assertThat(slots.findPublicByPlaceId(target.placeId(), AvailabilityStatus.ACTIVE, NOW))
                .extracting(PlaceAvailability::getId).containsExactlyElementsOf(expectedSlots);
        assertThat(slots.findPlaceIdsWithReservableAvailability(List.of(control.placeId(), target.placeId()), NOW))
                .containsExactlyInAnyOrderElementsOf(expectedSlots.isEmpty()
                        ? List.of(control.placeId()) : List.of(control.placeId(), target.placeId()));
    }

    private void assertNearby(Fixture control, Fixture target, boolean targetIncluded) {
        // 첫 페이지를 가득 채워 PageableExecutionUtils의 count 생략을 막는다.
        var first = nearby.findNearbyReservablePlaces(37.0, 127.0, 1000.0, null, null, 1,
                null, null, "NEAREST", NOW, PageRequest.of(0, 1));
        assertThat(first.getContent()).extracting(NearbyReservablePlaceProjection::getPlaceId)
                .containsExactly(control.placeId());
        assertThat(first.getTotalElements()).isEqualTo(targetIncluded ? 2L : 1L);
        var second = nearby.findNearbyReservablePlaces(37.0, 127.0, 1000.0, null, null, 1,
                null, null, "NEAREST", NOW, PageRequest.of(1, 1));
        assertThat(second.getTotalElements()).isEqualTo(targetIncluded ? 2L : 1L);
        if (targetIncluded) {
            assertThat(second.getContent()).extracting(NearbyReservablePlaceProjection::getPlaceId)
                    .containsExactly(target.placeId());
            assertThat(second.getContent()).extracting(NearbyReservablePlaceProjection::getAvailabilityId)
                    .containsExactly(target.slotId());
        } else {
            assertThat(second.getContent()).isEmpty();
        }
    }

    private void assertReserveRejected(Fixture target, AvailabilityErrorCode expected) {
        assertThatThrownBy(() -> availabilityService.reserve(target.slotId(), 1))
                .isInstanceOfSatisfying(AvailabilityException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(expected));
    }

    private Fixture fixture() {
        long ownerId = merchant();
        long placeId = jdbc.queryForObject("""
                INSERT INTO map_place (place_name, address, category, latitude, longitude, registrant, photo_count, location)
                VALUES ('자격 계약 장소', '서울', '음식점', 37.0, 127.0, 'test', 0,
                    ST_SetSRID(ST_MakePoint(127.0, 37.0), 4326)) RETURNING map_place_id
                """, Long.class);
        em.persist(MerchantOwnerPlace.builder().placeId(placeId).merchantOwnerUserId(ownerId).createdAt(NOW).build());
        long slotId = slot(ownerId, placeId, NOW.plusHours(2), NOW.plusHours(3));
        return new Fixture(ownerId, placeId, slotId);
    }

    private long merchant() {
        String unique = "eligibility-" + UUID.randomUUID();
        long ownerId = users.saveAndFlush(User.builder().username(unique).email(unique + "@example.invalid")
                .password("unused").birthYear(1995).language("ko").country("KR")
                .role(UserRole.MERCHANT_OWNER).build()).getId();
        em.persist(MerchantOwnerProfile.builder().userId(ownerId).businessName("계약 점주").displayName("계약 점주")
                .contactEmail("eligibility@example.invalid").contactPhone("010-0000-0000")
                .status(MerchantOwnerStatus.ACTIVE).createdAt(NOW).updatedAt(NOW).build());
        em.flush();
        em.persist(MerchantVerification.builder().userId(ownerId).legalName("테스트 점주").businessName("계약 점주")
                .encryptedBusinessRegistrationNumber("test-ciphertext")
                .identityStatus(MerchantVerificationStatus.APPROVED).businessStatus(MerchantVerificationStatus.APPROVED)
                .createdAt(NOW).updatedAt(NOW).build());
        em.flush();
        return ownerId;
    }

    private long slot(long ownerId, long placeId, LocalDateTime startsAt, LocalDateTime endsAt) {
        PlaceAvailability availability = PlaceAvailability.create(ownerId, placeId, startsAt, endsAt, 10, NOW);
        em.persist(availability);
        em.flush();
        return availability.getId();
    }

    private void changeMerchantState(MerchantCase scenario, Fixture target) {
        switch (scenario) {
            case ACTIVE -> { }
            case WITHDRAWN -> jdbc.update("UPDATE users SET status='WITHDRAWN' WHERE id=?", target.ownerId());
            case WRONG_ROLE -> jdbc.update("UPDATE users SET role='USER' WHERE id=?", target.ownerId());
            case PENDING_PROFILE -> jdbc.update("UPDATE merchant_owner_profile SET status='PENDING' WHERE user_id=?", target.ownerId());
            case REVOKED_PROFILE -> jdbc.update("UPDATE merchant_owner_profile SET status='REVOKED' WHERE user_id=?", target.ownerId());
            // 실제 FK가 슬롯·인증·매핑을 함께 지우므로 존재할 수 없는 고아 슬롯을 만들지 않는다.
            case MISSING_PROFILE -> jdbc.update("DELETE FROM merchant_owner_profile WHERE user_id=?", target.ownerId());
            case IDENTITY_PENDING -> jdbc.update("UPDATE merchant_verification SET identity_status='PENDING' WHERE user_id=?", target.ownerId());
            case BUSINESS_PENDING -> jdbc.update("UPDATE merchant_verification SET business_status='PENDING' WHERE user_id=?", target.ownerId());
            case MISSING_VERIFICATION -> jdbc.update("DELETE FROM merchant_verification WHERE user_id=?", target.ownerId());
            case PERMANENT_BAN -> jdbc.update("UPDATE users SET banned=TRUE, ban_type='PERMANENT', ban_expires_at=NULL WHERE id=?", target.ownerId());
            case TEMPORARY_BAN -> temporaryBan(target.ownerId(), NOW.plusSeconds(1));
            case EXPIRED_BAN -> temporaryBan(target.ownerId(), NOW.minusSeconds(1));
            case BAN_EXPIRES_NOW -> temporaryBan(target.ownerId(), NOW);
            case BAN_WITHOUT_EXPIRY -> temporaryBan(target.ownerId(), null);
            case OWNERSHIP_TRANSFERRED -> {
                long nextOwnerId = merchant();
                jdbc.update("UPDATE merchant_owner_place SET merchant_owner_user_id=? WHERE place_id=?", nextOwnerId, target.placeId());
            }
            case MISSING_OWNERSHIP -> jdbc.update("DELETE FROM merchant_owner_place WHERE place_id=?", target.placeId());
        }
    }

    private void temporaryBan(long ownerId, LocalDateTime expiresAt) {
        jdbc.update("UPDATE users SET banned=TRUE, ban_type='TEMPORARY', ban_expires_at=? WHERE id=?", expiresAt, ownerId);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private record Fixture(long ownerId, long placeId, long slotId) { }

    private enum MerchantCase {
        ACTIVE(true), WITHDRAWN(false), WRONG_ROLE(false), PENDING_PROFILE(false), REVOKED_PROFILE(false),
        MISSING_PROFILE(false), IDENTITY_PENDING(false), BUSINESS_PENDING(false), MISSING_VERIFICATION(false),
        PERMANENT_BAN(false), TEMPORARY_BAN(false), EXPIRED_BAN(true), BAN_EXPIRES_NOW(true),
        BAN_WITHOUT_EXPIRY(false), OWNERSHIP_TRANSFERRED(false), MISSING_OWNERSHIP(false);

        private final boolean allowed;

        MerchantCase(boolean allowed) {
            this.allowed = allowed;
        }
    }

    private enum SlotCase { STARTED, ENDED, INACTIVE, EXHAUSTED, RETIRED }

    private enum PlaceCase { HIDDEN, CLOSED, NO_LOCATION, OUTSIDE_RADIUS }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock eligibilityClock() {
            return CLOCK;
        }
    }
}
