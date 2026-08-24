package com.typenull.pingdom.integration.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.availability.domain.exception.AvailabilityErrorCode;
import com.typenull.pingdom.availability.domain.exception.AvailabilityException;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.TravelScheduleRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
import com.typenull.pingdom.offer.api.dto.CouponRedeemRequest;
import com.typenull.pingdom.offer.api.dto.CouponResponse;
import com.typenull.pingdom.offer.application.MerchantOfferService;
import com.typenull.pingdom.offer.application.TouristOfferService;
import com.typenull.pingdom.offer.domain.TouristCoupon;
import com.typenull.pingdom.offer.domain.TouristOffer;
import com.typenull.pingdom.offer.domain.exception.OfferErrorCode;
import com.typenull.pingdom.offer.domain.exception.OfferException;
import com.typenull.pingdom.offer.infrastructure.TouristCouponRepository;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.reservation.api.dto.ReservationCreateRequest;
import com.typenull.pingdom.reservation.api.dto.ReservationResponse;
import com.typenull.pingdom.reservation.application.ReservationService;
import com.typenull.pingdom.reservation.domain.ReservationStatus;
import com.typenull.pingdom.reservation.domain.exception.ReservationErrorCode;
import com.typenull.pingdom.reservation.domain.exception.ReservationException;
import com.typenull.pingdom.reservation.infrastructure.ReservationRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class CouponBookingConcurrencyIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

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

    @Autowired private TouristOfferService touristOfferService;
    @Autowired private MerchantOfferService merchantOfferService;
    @Autowired private ReservationService reservationService;
    @Autowired private UserRepository userRepository;
    @Autowired private TravelScheduleRepository travelScheduleRepository;
    @Autowired private MerchantOwnerProfileRepository profileRepository;
    @Autowired private MerchantVerificationRepository verificationRepository;
    @Autowired private MerchantOwnerPlaceRepository ownerPlaceRepository;
    @Autowired private MapPlaceRepository placeRepository;
    @Autowired private TouristOfferRepository offerRepository;
    @Autowired private TouristCouponRepository couponRepository;
    @Autowired private PlaceAvailabilityRepository availabilityRepository;
    @Autowired private ReservationRepository reservationRepository;

    @BeforeEach
    void cleanDatabase() {
        reservationRepository.deleteAllInBatch();
        availabilityRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        offerRepository.deleteAllInBatch();
        travelScheduleRepository.deleteAllInBatch();
        ownerPlaceRepository.deleteAllInBatch();
        verificationRepository.deleteAllInBatch();
        profileRepository.deleteAllInBatch();
        placeRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void concurrentCouponIssueDoesNotExceedOfferQuantity() throws Exception {
        MerchantContext merchant = merchant("coupon-capacity");
        User firstTourist = eligibleTourist("coupon-capacity-1");
        User secondTourist = eligibleTourist("coupon-capacity-2");
        TouristOffer offer = publishedOffer(merchant, "coupon-capacity", 1);

        List<ConcurrentScenario.Result<CouponResponse>> results = ConcurrentScenario.run(
                TIMEOUT,
                () -> touristOfferService.issue(firstTourist.getId(), offer.getId()),
                () -> touristOfferService.issue(secondTourist.getId(), offer.getId())
        );

        assertThat(results).filteredOn(ConcurrentScenario.Result::succeeded).hasSize(1);
        assertOfferFailures(results, OfferErrorCode.OFFER_SOLD_OUT);
        assertThat(couponRepository.count()).isOne();
        assertThat(offerRepository.findById(offer.getId()).orElseThrow().getIssuedQuantity()).isOne();
    }

    @Test
    void concurrentCouponIssueBySameTouristCreatesOneCoupon() throws Exception {
        MerchantContext merchant = merchant("coupon-idempotency");
        User tourist = eligibleTourist("coupon-idempotency");
        TouristOffer offer = publishedOffer(merchant, "coupon-idempotency", 2);

        List<ConcurrentScenario.Result<CouponResponse>> results = ConcurrentScenario.run(
                TIMEOUT,
                () -> touristOfferService.issue(tourist.getId(), offer.getId()),
                () -> touristOfferService.issue(tourist.getId(), offer.getId())
        );

        assertThat(results).filteredOn(ConcurrentScenario.Result::succeeded).hasSize(1);
        assertOfferFailures(results, OfferErrorCode.COUPON_ALREADY_ISSUED);
        assertThat(couponRepository.count()).isOne();
        assertThat(offerRepository.findById(offer.getId()).orElseThrow().getIssuedQuantity()).isOne();
    }

    @Test
    void concurrentCouponRedeemChangesStateOnlyOnce() throws Exception {
        MerchantContext merchant = merchant("coupon-redeem");
        User tourist = eligibleTourist("coupon-redeem");
        TouristOffer offer = publishedOffer(merchant, "coupon-redeem", 1);
        CouponResponse issued = touristOfferService.issue(tourist.getId(), offer.getId());

        List<ConcurrentScenario.Result<CouponResponse>> results = ConcurrentScenario.run(
                TIMEOUT,
                () -> merchantOfferService.redeem(merchant.user().getId(), new CouponRedeemRequest(issued.code())),
                () -> merchantOfferService.redeem(merchant.user().getId(), new CouponRedeemRequest(issued.code()))
        );

        assertThat(results).filteredOn(ConcurrentScenario.Result::succeeded).hasSize(1);
        assertOfferFailures(results, OfferErrorCode.COUPON_NOT_REDEEMABLE);
        TouristCoupon coupon = couponRepository.findAll().getFirst();
        assertThat(coupon.getRedeemedBy()).isEqualTo(merchant.user().getId());
        assertThat(coupon.getRedeemedAt()).isNotNull();
    }

    @Test
    void couponRedeemByOtherMerchantIsRejectedWithoutChangingCoupon() {
        MerchantContext owner = merchant("coupon-owner");
        MerchantContext otherMerchant = merchant("coupon-other-merchant");
        User tourist = eligibleTourist("coupon-permission");
        TouristOffer offer = publishedOffer(owner, "coupon-permission", 1);
        CouponResponse issued = touristOfferService.issue(tourist.getId(), offer.getId());

        assertThatThrownBy(() -> merchantOfferService.redeem(
                otherMerchant.user().getId(), new CouponRedeemRequest(issued.code())))
                .isInstanceOfSatisfying(OfferException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OfferErrorCode.COUPON_NOT_FOUND));

        TouristCoupon unchanged = couponRepository.findAll().getFirst();
        assertThat(unchanged.getRedeemedAt()).isNull();
        assertThat(unchanged.getRedeemedBy()).isNull();

        merchantOfferService.redeem(owner.user().getId(), new CouponRedeemRequest(issued.code()));
        assertThat(couponRepository.findAll().getFirst().getRedeemedBy()).isEqualTo(owner.user().getId());
    }

    @Test
    void failedDuplicateCouponIssueRollsBackAndAllowsAnotherTouristToRetry() {
        MerchantContext merchant = merchant("coupon-retry");
        User firstTourist = eligibleTourist("coupon-retry-1");
        User retryTourist = eligibleTourist("coupon-retry-2");
        TouristOffer offer = publishedOffer(merchant, "coupon-retry", 2);
        touristOfferService.issue(firstTourist.getId(), offer.getId());

        assertThatThrownBy(() -> touristOfferService.issue(firstTourist.getId(), offer.getId()))
                .isInstanceOfSatisfying(OfferException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(OfferErrorCode.COUPON_ALREADY_ISSUED));

        touristOfferService.issue(retryTourist.getId(), offer.getId());
        assertThat(couponRepository.count()).isEqualTo(2);
        assertThat(offerRepository.findById(offer.getId()).orElseThrow().getIssuedQuantity()).isEqualTo(2);
    }

    @Test
    void concurrentReservationsDoNotExceedAvailabilityCapacity() throws Exception {
        MerchantContext merchant = merchant("reservation-capacity");
        User firstTourist = tourist("reservation-capacity-1");
        User secondTourist = tourist("reservation-capacity-2");
        PlaceAvailability availability = availability(merchant, 1);

        List<ConcurrentScenario.Result<ReservationResponse>> results = ConcurrentScenario.run(
                TIMEOUT,
                () -> reservationService.create(firstTourist.getId(),
                        new ReservationCreateRequest(availability.getId(), "capacity-1", 1)),
                () -> reservationService.create(secondTourist.getId(),
                        new ReservationCreateRequest(availability.getId(), "capacity-2", 1))
        );

        assertThat(results).filteredOn(ConcurrentScenario.Result::succeeded).hasSize(1);
        assertAvailabilityFailures(results, AvailabilityErrorCode.AVAILABILITY_CAPACITY_EXCEEDED);
        assertThat(reservationRepository.count()).isOne();
        assertThat(availabilityRepository.findById(availability.getId()).orElseThrow().getRemainingCapacity()).isZero();
    }

    @Test
    void concurrentReservationWithSameIdempotencyKeyReturnsOneReservation() throws Exception {
        MerchantContext merchant = merchant("reservation-idempotency");
        User tourist = tourist("reservation-idempotency");
        PlaceAvailability availability = availability(merchant, 2);
        ReservationCreateRequest request = new ReservationCreateRequest(availability.getId(), "same-key", 1);

        List<ConcurrentScenario.Result<ReservationResponse>> results = ConcurrentScenario.run(
                TIMEOUT,
                () -> reservationService.create(tourist.getId(), request),
                () -> reservationService.create(tourist.getId(), request)
        );

        assertThat(results).allMatch(ConcurrentScenario.Result::succeeded);
        assertThat(results).extracting(result -> result.value().id()).containsOnly(results.getFirst().value().id());
        assertThat(reservationRepository.count()).isOne();
        assertThat(availabilityRepository.findById(availability.getId()).orElseThrow().getRemainingCapacity()).isOne();
    }

    @Test
    void concurrentReservationWithReusedIdempotencyKeyRejectsDifferentPayload() throws Exception {
        MerchantContext merchant = merchant("reservation-reused-key");
        User tourist = tourist("reservation-reused-key");
        PlaceAvailability availability = availability(merchant, 3);

        List<ConcurrentScenario.Result<ReservationResponse>> results = ConcurrentScenario.run(
                TIMEOUT,
                () -> reservationService.create(tourist.getId(),
                        new ReservationCreateRequest(availability.getId(), "reused-key", 1)),
                () -> reservationService.create(tourist.getId(),
                        new ReservationCreateRequest(availability.getId(), "reused-key", 2))
        );

        assertThat(results).filteredOn(ConcurrentScenario.Result::succeeded).hasSize(1);
        assertReservationFailures(results, ReservationErrorCode.IDEMPOTENCY_KEY_REUSED);
        int reservedQuantity = results.stream().filter(ConcurrentScenario.Result::succeeded)
                .mapToInt(result -> result.value().quantity()).sum();
        assertThat(reservationRepository.count()).isOne();
        assertThat(availabilityRepository.findById(availability.getId()).orElseThrow().getRemainingCapacity())
                .isEqualTo(3 - reservedQuantity);
    }

    @Test
    void concurrentReservationCancelReleasesCapacityOnlyOnce() throws Exception {
        MerchantContext merchant = merchant("reservation-cancel");
        User tourist = tourist("reservation-cancel");
        PlaceAvailability availability = availability(merchant, 1);
        ReservationResponse created = reservationService.create(tourist.getId(),
                new ReservationCreateRequest(availability.getId(), "cancel-key", 1));

        List<ConcurrentScenario.Result<ReservationResponse>> results = ConcurrentScenario.run(
                TIMEOUT,
                () -> reservationService.cancelMine(tourist.getId(), created.id()),
                () -> reservationService.cancelMine(tourist.getId(), created.id())
        );

        assertThat(results).filteredOn(ConcurrentScenario.Result::succeeded).hasSize(1);
        assertReservationFailures(results, ReservationErrorCode.INVALID_RESERVATION_STATE);
        assertThat(reservationRepository.findById(created.id()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CANCELED);
        assertThat(availabilityRepository.findById(availability.getId()).orElseThrow().getRemainingCapacity()).isOne();
    }

    @Test
    void reservationChangesByNonOwnersAreRejectedWithoutChangingState() {
        MerchantContext owner = merchant("reservation-owner");
        MerchantContext otherMerchant = merchant("reservation-other-merchant");
        User tourist = tourist("reservation-owner");
        User otherTourist = tourist("reservation-other-tourist");
        PlaceAvailability availability = availability(owner, 1);
        ReservationResponse created = reservationService.create(tourist.getId(),
                new ReservationCreateRequest(availability.getId(), "permission-key", 1));

        assertThatThrownBy(() -> reservationService.confirm(otherMerchant.user().getId(), created.id()))
                .isInstanceOfSatisfying(ReservationException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_FORBIDDEN));
        assertThatThrownBy(() -> reservationService.cancelOwned(otherMerchant.user().getId(), created.id()))
                .isInstanceOfSatisfying(ReservationException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_FORBIDDEN));
        assertThatThrownBy(() -> reservationService.cancelMine(otherTourist.getId(), created.id()))
                .isInstanceOfSatisfying(ReservationException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_FORBIDDEN));

        assertThat(reservationRepository.findById(created.id()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDING);
        reservationService.confirm(owner.user().getId(), created.id());
        assertThat(reservationRepository.findById(created.id()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void failedReservationForInsufficientCapacityCanRetryAfterCapacityIsReleased() {
        MerchantContext merchant = merchant("reservation-retry");
        User firstTourist = tourist("reservation-retry-1");
        User retryTourist = tourist("reservation-retry-2");
        PlaceAvailability availability = availability(merchant, 1);
        ReservationResponse first = reservationService.create(firstTourist.getId(),
                new ReservationCreateRequest(availability.getId(), "first-key", 1));

        assertThatThrownBy(() -> reservationService.create(retryTourist.getId(),
                new ReservationCreateRequest(availability.getId(), "retry-key", 1)))
                .isInstanceOfSatisfying(AvailabilityException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                AvailabilityErrorCode.AVAILABILITY_CAPACITY_EXCEEDED));

        reservationService.cancelMine(firstTourist.getId(), first.id());
        ReservationResponse retried = reservationService.create(retryTourist.getId(),
                new ReservationCreateRequest(availability.getId(), "retry-key", 1));
        assertThat(retried.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservationRepository.count()).isEqualTo(2);
        assertThat(availabilityRepository.findById(availability.getId()).orElseThrow().getRemainingCapacity()).isZero();
    }

    private MerchantContext merchant(String suffix) {
        LocalDateTime now = now();
        User user = userRepository.saveAndFlush(user(suffix, UserRole.MERCHANT_OWNER));
        MapPlace place = placeRepository.saveAndFlush(MapPlace.builder()
                .name("동시성 테스트 장소 " + suffix)
                .address("서울특별시 중구 " + suffix)
                .latitude(37.5665)
                .longitude(126.9780)
                .userId(user.getId())
                .registrant(user.getUsername())
                .build());
        profileRepository.saveAndFlush(MerchantOwnerProfile.builder()
                .userId(user.getId())
                .businessName("동시성 테스트 상점")
                .displayName("Concurrency Merchant")
                .contactEmail(suffix + "@example.com")
                .contactPhone("010-1234-5678")
                .status(MerchantOwnerStatus.ACTIVE)
                .createdAt(now.minusDays(1))
                .updatedAt(now.minusDays(1))
                .build());
        verificationRepository.saveAndFlush(MerchantVerification.builder()
                .userId(user.getId())
                .legalName("Concurrency Owner")
                .businessName("동시성 테스트 상점")
                .encryptedBusinessRegistrationNumber("encrypted-" + suffix)
                .identityStatus(MerchantVerificationStatus.APPROVED)
                .businessStatus(MerchantVerificationStatus.APPROVED)
                .createdAt(now.minusDays(1))
                .updatedAt(now.minusDays(1))
                .build());
        ownerPlaceRepository.saveAndFlush(MerchantOwnerPlace.builder()
                .merchantOwnerUserId(user.getId())
                .placeId(place.getId())
                .createdAt(now.minusDays(1))
                .build());
        return new MerchantContext(user, place);
    }

    private User eligibleTourist(String suffix) {
        User tourist = tourist(suffix);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        travelScheduleRepository.saveAndFlush(TravelSchedule.create(tourist, today.minusDays(1), today.plusDays(1)));
        return tourist;
    }

    private User tourist(String suffix) {
        return userRepository.saveAndFlush(user(suffix + "-tourist", UserRole.USER));
    }

    private User user(String suffix, UserRole role) {
        return User.builder()
                .username("concurrency-" + suffix)
                .email("concurrency-" + suffix + "@example.com")
                .emailVerified(true)
                .password("encoded-password")
                .birthYear(1990)
                .language("ko")
                .country("KR")
                .role(role)
                .status(UserStatus.ACTIVE)
                .banned(false)
                .build();
    }

    private TouristOffer publishedOffer(MerchantContext merchant, String suffix, int quantity) {
        LocalDateTime now = now();
        TouristOffer offer = TouristOffer.draft(
                merchant.user().getId(), merchant.place().getId(), "Offer " + suffix, "동시성 테스트",
                "테스트 혜택", now.minusHours(1), now.plusDays(1), quantity, 1, now.minusDays(1));
        offer.publish(now.minusMinutes(1));
        return offerRepository.saveAndFlush(offer);
    }

    private PlaceAvailability availability(MerchantContext merchant, int capacity) {
        LocalDateTime now = now();
        return availabilityRepository.saveAndFlush(PlaceAvailability.create(
                merchant.user().getId(), merchant.place().getId(), AvailabilityProductType.GENERAL,
                now.plusHours(1), now.plusHours(2), capacity, now));
    }

    private <T> void assertOfferFailures(
            List<ConcurrentScenario.Result<T>> results,
            OfferErrorCode errorCode
    ) {
        assertThat(results).filteredOn(result -> result.failure() != null)
                .extracting(ConcurrentScenario.Result::failure)
                .allSatisfy(failure -> {
                    assertThat(failure).isInstanceOf(OfferException.class);
                    assertThat(((OfferException) failure).getErrorCode()).isEqualTo(errorCode);
                });
    }

    private <T> void assertAvailabilityFailures(
            List<ConcurrentScenario.Result<T>> results,
            AvailabilityErrorCode errorCode
    ) {
        assertThat(results).filteredOn(result -> result.failure() != null)
                .extracting(ConcurrentScenario.Result::failure)
                .allSatisfy(failure -> {
                    assertThat(failure).isInstanceOf(AvailabilityException.class);
                    assertThat(((AvailabilityException) failure).getErrorCode()).isEqualTo(errorCode);
                });
    }

    private <T> void assertReservationFailures(
            List<ConcurrentScenario.Result<T>> results,
            ReservationErrorCode errorCode
    ) {
        assertThat(results).filteredOn(result -> result.failure() != null)
                .extracting(ConcurrentScenario.Result::failure)
                .allSatisfy(failure -> {
                    assertThat(failure).isInstanceOf(ReservationException.class);
                    assertThat(((ReservationException) failure).getErrorCode()).isEqualTo(errorCode);
                });
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private record MerchantContext(User user, MapPlace place) {
    }
}
