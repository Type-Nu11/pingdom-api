package com.typenull.pingdom.reservation.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.availability.domain.*;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.identity.domain.*;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.application.service.conversion.PlaceConversionEventService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.reservation.api.dto.*;
import com.typenull.pingdom.reservation.domain.exception.*;
import com.typenull.pingdom.reservation.infrastructure.*;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

/** 실제 사용자/슬롯 행 잠금과 트랜잭션을 검증. 점주 자격과 전환 이벤트 발행은 별도 기존 테스트의 범위. */
@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "pingdom.dev-profile.enabled=true",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create", "outbox.enabled=false"})
@ActiveProfiles("dev")
class ReservationConfirmationPostgreSqlIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName
            .parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired ReservationService service;
    @Autowired ReservationQuoteService quoteService;
    @Autowired ReservationQuoteRepository quotes;
    @Autowired ReservationRepository reservations;
    @Autowired UserRepository users;
    @Autowired MapPlaceRepository places;
    @Autowired Clock clock;
    @SpyBean PlaceAvailabilityRepository slots;
    @MockBean PlaceConversionEventService events;
    private Long userId;
    private Long placeId;
    private Long slotId;

    @BeforeEach
    void fixture() {
        userId = users.saveAndFlush(User.builder().username("reservation-" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@example.com").password("test").birthYear(1998)
                .language("ko").country("KR").role(UserRole.USER).build()).getId();
        placeId = places.saveAndFlush(MapPlace.builder().name("실제 테스트 장소").address("테스트 주소")
                .latitude(37.0).longitude(127.0).userId(userId).build()).getId();
        var slot = PlaceAvailability.create(7L, placeId, LocalDateTime.now(clock).plusDays(1),
                LocalDateTime.now(clock).plusDays(1).plusHours(1), 10, LocalDateTime.now(clock));
        slot.setReservationTerms(new ReservationTerms(1000, 50, "KRW", "Asia/Seoul", true, 60), LocalDateTime.now(clock));
        slotId = slots.saveAndFlush(slot).getId();
        doAnswer(call -> slots.findByIdForUpdate(call.getArgument(0)))
                .when(slots).findReservableByIdForUpdate(anyLong(), any());
    }

    private ReservationCreateRequest request(String token) {
        return new ReservationCreateRequest(slotId, UUID.randomUUID().toString(), 2, "테스트 예약자", "01000000000", null, token);
    }

    @Test
    void simultaneousSameIntentCreatesOneReservationAndTakesCapacityOnce() throws Exception {
        var quote = quoteService.issue(userId, placeId, slotId, 2);
        var request = request(quote.confirmationToken());
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<ReservationResponse> submit = () -> { start.await(); return service.create(userId, request); };
            var first = executor.submit(submit);
            var second = executor.submit(submit);
            start.countDown();
            var accepted = first.get(15, TimeUnit.SECONDS);
            assertThat(second.get(15, TimeUnit.SECONDS).id()).isEqualTo(accepted.id());
            assertThat(accepted.confirmation()).isEqualTo(quote.confirmation());
            assertThat(slots.findById(slotId).orElseThrow().getRemainingCapacity()).isEqualTo(8);
            assertThat(reservations.findByTouristUserIdAndIdempotencyKey(userId, request.idempotencyKey()).orElseThrow()
                    .getConfirmation()).isEqualTo(quote.confirmation());
            verify(events, times(1)).publish(eq(userId), eq(placeId), any(), anyLong(), any());
        } finally { executor.shutdownNow(); }
    }

    @Test
    void finalRejectionCommitsAndReplaysWithoutTakingCapacity() {
        var quote = quoteService.issue(userId, placeId, slotId, 2);
        var request = request(quote.confirmationToken());
        var slot = slots.findById(slotId).orElseThrow();
        slot.setReservationTerms(new ReservationTerms(2000, 0, "KRW", "Asia/Seoul", false, null), LocalDateTime.now(clock));
        slots.saveAndFlush(slot);
        assertThatThrownBy(() -> service.create(userId, request)).isInstanceOfSatisfying(ReservationException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ReservationErrorCode.QUOTE_CONDITIONS_CHANGED));
        assertThat(quotes.findById(quote.confirmationToken()).orElseThrow().getRejectionCode())
                .isEqualTo(ReservationErrorCode.QUOTE_CONDITIONS_CHANGED);
        assertThatThrownBy(() -> service.create(userId, request)).isInstanceOf(ConfirmedReservationRejectedException.class);
        assertThat(slots.findById(slotId).orElseThrow().getRemainingCapacity()).isEqualTo(10);
        assertThat(reservations.findByTouristUserIdAndIdempotencyKey(userId, request.idempotencyKey())).isEmpty();
        verifyNoInteractions(events);
    }

    @Test
    void unknownFailureRollsBackReceiptReservationAndStockAndAllowsSameIntentRetry() {
        var quote = quoteService.issue(userId, placeId, slotId, 2);
        var request = request(quote.confirmationToken());
        doThrow(new IllegalStateException("test event failure")).when(events).publish(anyLong(), anyLong(), any(), anyLong(), any());
        assertThatThrownBy(() -> service.create(userId, request)).isInstanceOf(IllegalStateException.class);
        assertThat(quotes.findById(quote.confirmationToken()).orElseThrow().getIdempotencyKey()).isNull();
        assertThat(slots.findById(slotId).orElseThrow().getRemainingCapacity()).isEqualTo(10);
        assertThat(reservations.findByTouristUserIdAndIdempotencyKey(userId, request.idempotencyKey())).isEmpty();
        reset(events);
        assertThat(service.create(userId, request).confirmation()).isEqualTo(quote.confirmation());
        assertThat(slots.findById(slotId).orElseThrow().getRemainingCapacity()).isEqualTo(8);
    }
}
