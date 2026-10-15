package com.typenull.pingdom.reservation.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.availability.domain.*;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.identity.domain.*;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.payment.infrastructure.PaymentTransactionRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.reservation.api.dto.*;
import com.typenull.pingdom.reservation.domain.*;
import com.typenull.pingdom.reservation.domain.exception.*;
import com.typenull.pingdom.reservation.infrastructure.ReservationQuoteRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import static com.typenull.pingdom.reservation.domain.exception.ReservationErrorCode.*;

class ReservationQuoteServiceTest {
    private final ReservationQuoteRepository quotes = mock(ReservationQuoteRepository.class);
    private final PlaceAvailabilityRepository slots = mock(PlaceAvailabilityRepository.class);
    private final MapPlaceRepository places = mock(MapPlaceRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PaymentTransactionRepository payments = mock(PaymentTransactionRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneOffset.UTC);
    private final ReservationQuoteService service = new ReservationQuoteService(quotes, slots, places,
            users, payments, new ObjectMapper().findAndRegisterModules(), clock);
    private final Map<String, ReservationQuote> saved = new HashMap<>();
    private PlaceAvailability slot;

    @BeforeEach
    void setup() {
        User user = mock(User.class);
        when(user.getRole()).thenReturn(UserRole.USER);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        slot = PlaceAvailability.create(7L, 11L, LocalDateTime.now(clock).plusDays(1),
                LocalDateTime.now(clock).plusDays(1).plusHours(1), 10, LocalDateTime.now(clock));
        ReflectionTestUtils.setField(slot, "id", 9L);
        slot.updateReservationTerms(new ReservationTerms("Asia/Seoul", true, 60), LocalDateTime.now(clock));
        when(slots.findByIdForUpdate(9L)).thenReturn(Optional.of(slot));
        when(slots.findReservableByIdForUpdate(eq(9L), any())).thenReturn(Optional.of(slot));
        MapPlace place = mock(MapPlace.class);
        when(place.getName()).thenReturn("확인 가능한 장소");
        when(places.findById(11L)).thenReturn(Optional.of(place));
        when(quotes.save(any())).thenAnswer(call -> {
            ReservationQuote quote = call.getArgument(0);
            saved.put(quote.getId(), quote);
            return quote;
        });
        when(quotes.findByIdForUpdate(anyString())).thenAnswer(call -> Optional.ofNullable(saved.get(call.getArgument(0))));
        when(quotes.findByTouristUserIdAndIdempotencyKey(anyLong(), anyString())).thenAnswer(call ->
                saved.values().stream().filter(q -> q.getTouristUserId().equals(call.getArgument(0))
                        && Objects.equals(q.getIdempotencyKey(), call.getArgument(1))).findFirst());
    }

    private ReservationQuoteResponse issue() { return service.issue(1L, 11L, 9L, 2); }
    private ReservationCreateRequest request(String token) {
        return new ReservationCreateRequest(9L, "intent-1", 2, "이름", "01012345678", null, token);
    }
    private void rejects(Runnable action, ReservationErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ReservationException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }

    @Test
    void providesReservationConditionsWithoutTakingCapacity() {
        var response = issue();
        var quote = response.confirmation();
        assertThat(quote.placeName()).isEqualTo("확인 가능한 장소");
        assertThat(quote.startsAt().toInstant()).isEqualTo(clock.instant().plusSeconds(86400));
        assertThat(quote.startsAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(quote.cancellationDeadline()).isEqualTo(quote.startsAt().minusHours(1));
        assertThat(slot.getRemainingCapacity()).isEqualTo(10);
        assertThat(service.verify(1L, request(response.confirmationToken()))).isEqualTo(quote);
        verifyNoInteractions(payments);
    }

    @Test
    void jsonPersistencePreservesDisplayedTimezoneAndConfirmationEquality() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var confirmation = issue().confirmation();
        assertThat(mapper.readValue(mapper.writeValueAsBytes(confirmation), ReservationConfirmation.class))
                .isEqualTo(confirmation);
    }

    @Test
    void legacyJsonCanBeReadWithoutReexposingProductOrFinancialFields() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var current = issue().confirmation();
        var legacy = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(current);
        legacy.put("productType", "TICKET").put("productId", 31).put("productName", "기존 상품")
                .put("unitAmountMinor", 1000).put("additionalAmountMinor", 50)
                .put("totalAmountMinor", 2050).put("currency", "KRW")
                .put("currencyFractionDigits", 0).put("paymentRequired", true)
                .put("cancellationFeeMinor", 0).put("refundableAmountMinor", 2050).put("productVersion", 1);
        var restored = mapper.treeToValue(legacy, ReservationConfirmation.class);
        assertThat(restored).isEqualTo(current);
        var booking = Reservation.create(1L, 9L, "legacy", 2, LocalDateTime.now(clock));
        booking.acceptConfirmation("11111111-1111-1111-1111-111111111111", restored);
        ReflectionTestUtils.setField(booking, "legacyConfirmation", legacy);
        var response = mapper.valueToTree(ReservationResponse.from(booking));
        assertThat(response.has("productId")).isFalse();
        assertThat(response.has("productType")).isFalse();
        assertThat(response.has("legacyConfirmation")).isFalse();
        assertThat(response.path("confirmation").size()).isEqualTo(11);
        for (String removed : List.of("productType", "productId", "productName", "productVersion", "unitAmountMinor",
                "additionalAmountMinor", "totalAmountMinor", "currency", "currencyFractionDigits", "paymentRequired",
                "cancellationFeeMinor", "refundableAmountMinor")) {
            assertThat(response.path("confirmation").has(removed)).as(removed).isFalse();
        }
    }

    @Test
    void legacyPaidReservationWithoutConfirmationStillRequiresRefundBeforeCancellation() {
        var booking = Reservation.create(1L, 9L, "legacy", 2, LocalDateTime.now(clock));
        when(payments.findFirstByReservationIdAndStatusIn(isNull(), any()))
                .thenReturn(Optional.of(mock(com.typenull.pingdom.payment.domain.PaymentTransaction.class)));
        rejects(() -> service.requireCancellationAllowed(booking), RESERVATION_REFUND_REQUIRED);
    }

    @Test
    void distinguishesNonCancellableFromUnconfigured() {
        slot.updateReservationTerms(new ReservationTerms("UTC", false, null), LocalDateTime.now(clock));
        var quote = issue().confirmation();
        assertThat(quote.cancellationDeadline()).isNull();
        ReflectionTestUtils.setField(slot, "reservationTerms", null);
        rejects(this::issue, QUOTE_TERMS_UNAVAILABLE);
    }

    @Test
    void rejectsExpiryAtBoundaryAndReplaysFinalRejection() {
        var response = issue();
        var expired = new ReservationQuoteService(quotes, slots, places, users, payments,
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(response.confirmation().expiresAt(), ZoneOffset.UTC));
        var request = request(response.confirmationToken());
        rejects(() -> expired.verify(1L, request), QUOTE_EXPIRED);
        // 원인이 다시 유효해져도 확정 거절 영수증은 같은 결과로 복구.
        rejects(() -> service.checkPriorResult(1L, request), QUOTE_EXPIRED);
        rejects(() -> service.checkPriorResult(1L, new ReservationCreateRequest(9L, "intent-1", 2)), IDEMPOTENCY_KEY_REUSED);
    }


    @Test
    void rejectsExpiryWhileWaitingForSlotLock() {
        var response = issue();
        var instant = new java.util.concurrent.atomic.AtomicReference<>(response.confirmation().expiresAt().minusNanos(1));
        Clock advancing = mock(Clock.class);
        when(advancing.getZone()).thenReturn(ZoneOffset.UTC);
        when(advancing.instant()).thenAnswer(call -> instant.get());
        var verifier = new ReservationQuoteService(quotes, slots, places, users, payments,
                new ObjectMapper().findAndRegisterModules(), advancing);
        when(slots.findByIdForUpdate(9L)).thenAnswer(call -> {
            instant.set(response.confirmation().expiresAt());
            return Optional.of(slot);
        });
        var request = request(response.confirmationToken());
        rejects(() -> verifier.verify(1L, request), QUOTE_EXPIRED);
        rejects(() -> service.checkPriorResult(1L, request), QUOTE_EXPIRED);
        assertThat(slot.getRemainingCapacity()).isEqualTo(10);
    }

    @Test
    void oldContractTokenRequiresReconfirmationWithoutTakingCapacity() {
        var response = issue();
        ReflectionTestUtils.setField(saved.get(response.confirmationToken()), "contractVersion", 1);
        rejects(() -> service.verify(1L, request(response.confirmationToken())), QUOTE_CONDITIONS_CHANGED);
        rejects(() -> service.checkPriorResult(1L, request(response.confirmationToken())), QUOTE_CONDITIONS_CHANGED);
        assertThat(slot.getRemainingCapacity()).isEqualTo(10);
    }

    @Test
    void retiredSlotCannotIssueOrConsumeQuote() {
        var response = issue();
        ReflectionTestUtils.setField(slot, "bookingRetired", true);
        rejects(this::issue, RESERVATION_SLOT_INACTIVE);
        rejects(() -> service.verify(1L, request(response.confirmationToken())), RESERVATION_SLOT_INACTIVE);
    }

    @Test
    void rejectsHourlyLimitBeforeLockingSlotOrWritingQuote() {
        when(quotes.countByTouristUserIdAndCreatedAtAfter(eq(1L), any())).thenReturn(60L);
        rejects(this::issue, QUOTE_RATE_LIMITED);
        verifyNoInteractions(slots);
        verify(quotes, never()).save(any());
    }

    @Test
    void rejectsUnusedLimitBeforeLockingSlotOrWritingQuote() {
        when(quotes.countByTouristUserIdAndCreatedAtAfter(eq(1L), any())).thenReturn(59L);
        when(quotes.countByTouristUserIdAndIdempotencyKeyIsNull(1L)).thenReturn(120L);
        rejects(this::issue, QUOTE_RATE_LIMITED);
        verifyNoInteractions(slots);
        verify(quotes, never()).save(any());
    }

    @Test
    void permitsIssueImmediatelyBelowBothLimits() {
        when(quotes.countByTouristUserIdAndCreatedAtAfter(eq(1L), any())).thenReturn(59L);
        when(quotes.countByTouristUserIdAndIdempotencyKeyIsNull(1L)).thenReturn(119L);
        assertThat(issue().confirmationToken()).isNotBlank();
        var order = inOrder(users, quotes, slots);
        order.verify(users).findByIdForUpdate(1L);
        order.verify(quotes).countByTouristUserIdAndCreatedAtAfter(1L, clock.instant().minusSeconds(3600));
        order.verify(quotes).countByTouristUserIdAndIdempotencyKeyIsNull(1L);
        order.verify(slots).findByIdForUpdate(9L);
    }

    @Test
    void cleanupDrainsBacklogAndStopsWhenLastBatchIsPartial() {
        Instant cutoff = clock.instant().minusSeconds(86400);
        when(quotes.deleteUnusedBefore(cutoff)).thenReturn(1000, 1000, 12);
        service.removeUnusedExpiredQuotes();
        verify(quotes, times(3)).deleteUnusedBefore(cutoff);
    }

    @Test
    void cleanupBoundsFullBatchesAndSkipsFurtherWorkWhenEmpty() {
        Instant cutoff = clock.instant().minusSeconds(86400);
        when(quotes.deleteUnusedBefore(cutoff)).thenReturn(1000);
        service.removeUnusedExpiredQuotes();
        verify(quotes, times(20)).deleteUnusedBefore(cutoff);
        clearInvocations(quotes);
        when(quotes.deleteUnusedBefore(cutoff)).thenReturn(0);
        service.removeUnusedExpiredQuotes();
        verify(quotes).deleteUnusedBefore(cutoff);
    }

    @Test
    void rejectsOtherUserAndDoesNotConsumeToken() {
        var response = issue();
        rejects(() -> service.verify(2L, request(response.confirmationToken())), QUOTE_NOT_FOUND);
        assertThat(saved.get(response.confirmationToken()).getIdempotencyKey()).isNull();
    }

    @Test
    void rejectsChangedQuantityAndOtherBodyOnSameKey() {
        var response = issue();
        var different = new ReservationCreateRequest(9L, "intent-1", 3, "이름", "01012345678", null, response.confirmationToken());
        rejects(() -> service.verify(1L, different), QUOTE_REQUEST_MISMATCH);
        rejects(() -> service.checkPriorResult(1L, request(response.confirmationToken())), IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void changedTermsAndSlotTimeInvalidateQuote() {
        var first = issue();
        slot.updateReservationTerms(new ReservationTerms("UTC", false, null), LocalDateTime.now(clock));
        rejects(() -> service.verify(1L, request(first.confirmationToken())), QUOTE_CONDITIONS_CHANGED);
        var next = issue();
        slot.update(slot.getStartsAt().plusHours(1), slot.getEndsAt().plusHours(1), 10, LocalDateTime.now(clock));
        rejects(() -> service.verify(1L, request(next.confirmationToken())), QUOTE_CONDITIONS_CHANGED);
    }

    @Test
    void remainingCapacityChangesDoNotInvalidateUnchangedConditions() {
        var response = issue();
        slot.reserve(1, LocalDateTime.now(clock));
        assertThat(service.verify(1L, request(response.confirmationToken()))).isEqualTo(response.confirmation());
    }

    @Test
    void rejectsVanishedInactiveAndFullSlotsWithDistinctCodes() {
        var response = issue();
        when(slots.findByIdForUpdate(9L)).thenReturn(Optional.empty());
        rejects(() -> service.verify(1L, request(response.confirmationToken())), RESERVATION_SLOT_NOT_FOUND);
        when(slots.findByIdForUpdate(9L)).thenReturn(Optional.of(slot));
        var inactive = issue();
        slot.deactivate(LocalDateTime.now(clock));
        rejects(() -> service.verify(1L, request(inactive.confirmationToken())), RESERVATION_SLOT_INACTIVE);
        slot.activate(LocalDateTime.now(clock));
        var full = issue();
        slot.reserve(10, LocalDateTime.now(clock));
        rejects(() -> service.verify(1L, request(full.confirmationToken())), RESERVATION_CAPACITY_EXCEEDED);
    }


    @Test
    void oneTokenCannotCreateMultipleIntents() {
        var response = issue();
        service.verify(1L, request(response.confirmationToken()));
        var other = new ReservationCreateRequest(9L, "intent-2", 2, "이름", "01012345678", null, response.confirmationToken());
        rejects(() -> service.verify(1L, other), IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void cancellationUsesAcceptedPolicyEvenAfterMerchantChanges() {
        var response = issue();
        Reservation reservation = Reservation.create(1L, 9L, "intent-1", 2, LocalDateTime.now(clock));
        reservation.acceptConfirmation(response.confirmationToken(), response.confirmation());
        slot.updateReservationTerms(new ReservationTerms("UTC", false, null), LocalDateTime.now(clock));
        service.requireCancellationAllowed(reservation);
        when(payments.findFirstByReservationIdAndStatusIn(isNull(), any())).thenReturn(Optional.of(mock(com.typenull.pingdom.payment.domain.PaymentTransaction.class)));
        rejects(() -> service.requireCancellationAllowed(reservation), RESERVATION_REFUND_REQUIRED);
    }

}
