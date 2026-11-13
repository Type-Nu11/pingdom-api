package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.domain.*;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.verification.api.dto.*;
import com.typenull.pingdom.verification.domain.*;
import com.typenull.pingdom.verification.domain.exception.*;
import com.typenull.pingdom.verification.infrastructure.*;
import com.typenull.pingdom.verification.infrastructure.VisitVerificationSessionRepository.ForegroundRecoveryCandidate;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

class VisitVerificationForegroundRecoveryTest {
    private static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z");
    private final VisitVerificationSessionRepository sessions = mock(VisitVerificationSessionRepository.class);
    private final MapPlaceRepository places = mock(MapPlaceRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private VisitVerificationService service;

    @BeforeEach
    void setup() {
        VisitVerificationProperties properties = new VisitVerificationProperties(500.0, Map.of(), 20.0,
                Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofSeconds(15), Duration.ofSeconds(5),
                Duration.ofMinutes(1), Duration.ofSeconds(10), Duration.ofDays(30), 1000.0, Duration.ofSeconds(30));
        service = new VisitVerificationService(sessions, mock(LocationCheckInRepository.class), users, places,
                Clock.fixed(NOW, ZoneOffset.UTC), properties, new VisitVerificationPolicyResolver(properties));
        when(users.findById(1L)).thenReturn(Optional.of(
                User.builder().id(1L).role(UserRole.USER).status(UserStatus.ACTIVE).build()));
    }

    @ParameterizedTest
    @CsvSource({"1,0", "20,0", "20,10", "20,19", "100,0", "100,50", "100,99",
            "1,-1", "20,-1", "100,-1"})
    void scansThroughLastCandidateWithoutPerPlaceLookups(int count, int selectedIndex) {
        List<VisitVerificationSession> entities = IntStream.range(0, count)
                .mapToObj(index -> session(1000L - index, NOW.minusMillis(index), 1000.0)).toList();
        List<ForegroundRecoveryCandidate> candidates = IntStream.range(0, count)
                .mapToObj(index -> candidate(entities.get(index),
                        Map.of("latitude", index == selectedIndex ? 35.1801 : 36.1801))).toList();
        stubBatches(candidates);
        if (selectedIndex >= 0) {
            VisitVerificationSession selected = entities.get(selectedIndex);
            when(sessions.findByIdAndTouristUserIdForUpdate(selected.getId(), 1L)).thenReturn(Optional.of(selected));
            assertThat(service.startForeground(1L, request(10.0)).id()).isEqualTo(selected.getId());
            verify(places, never()).findNearbyPlacesForVisitVerification(anyDouble(), anyDouble(), anyDouble(), any());
        } else {
            assertThatThrownBy(() -> service.startForeground(1L, request(10.0)))
                    .isInstanceOfSatisfying(VisitorVerificationException.class, error ->
                            assertThat(error.getErrorCode()).isEqualTo(VisitorVerificationErrorCode.FOREGROUND_VISIT_PLACE_NOT_FOUND));
        }
        int expectedBatches = selectedIndex >= 0 ? selectedIndex / 50 + 1 : count / 50 + 1;
        verify(sessions, times(expectedBatches)).findForegroundRecoveryBatch(eq(1L), any(), anyCollection(),
                any(), any(), any());
        verify(places, never()).findById(any());
        verify(sessions, never()).saveAndFlush(any());
    }

    @Test
    void preservesOriginalCursorAfterExpiringAnEntireBatch() {
        List<VisitVerificationSession> expired = IntStream.range(0, 50)
                .mapToObj(index -> expiredSession(1000L - index, NOW.minusMillis(index))).toList();
        VisitVerificationSession selected = session(950L, NOW.minusMillis(50), 1000.0);
        List<ForegroundRecoveryCandidate> candidates = new ArrayList<>();
        expired.forEach(session -> candidates.add(candidate(session, Map.of())));
        candidates.add(candidate(selected, Map.of()));
        stubBatches(candidates);
        when(sessions.expireForegroundPrefix(eq(1L), any(), anyCollection(), anyCollection(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Collection<Long> ids = invocation.getArgument(2);
                    // DB 만료 갱신과 같은 키 변경이 있어도 이미 읽은 projection 커서는 유지되어야 합니다.
                    expired.forEach(session -> session.expire(NOW));
                    return ids.size();
                });
        when(sessions.findByIdAndTouristUserIdForUpdate(950L, 1L)).thenReturn(Optional.of(selected));

        assertThat(service.startForeground(1L, request(10.0)).id()).isEqualTo(950L);

        verify(sessions).findForegroundRecoveryBatch(eq(1L), any(), anyCollection(),
                eq(NOW.minusMillis(49)), eq(951L), any());
        verify(sessions).expireForegroundPrefix(eq(1L), any(), argThat(ids -> ids.size() == 50),
                anyCollection(), eq(VisitVerificationSessionStatus.EXPIRED), eq(NOW), eq(NOW.minusSeconds(15)));
    }

    @Test
    void expiresOnlyVisitedPrefixBeforeFirstReusableSession() {
        VisitVerificationSession first = expiredSession(3L, NOW);
        VisitVerificationSession selected = session(2L, NOW.minusMillis(1), 1000.0);
        VisitVerificationSession unvisited = expiredSession(1L, NOW.minusMillis(2));
        stubBatches(List.of(candidate(first, Map.of()), candidate(selected, Map.of()), candidate(unvisited, Map.of())));
        when(sessions.expireForegroundPrefix(eq(1L), any(), eq(List.of(3L)), anyCollection(), any(), any(), any()))
                .thenReturn(1);
        when(sessions.findByIdAndTouristUserIdForUpdate(2L, 1L)).thenReturn(Optional.of(selected));

        assertThat(service.startForeground(1L, request(10.0)).id()).isEqualTo(2L);

        verify(sessions).expireForegroundPrefix(eq(1L), any(), eq(List.of(3L)), anyCollection(),
                eq(VisitVerificationSessionStatus.EXPIRED), eq(NOW), eq(NOW.minusSeconds(15)));
        verify(sessions, never()).findByIdAndTouristUserIdForUpdate(1L, 1L);
    }

    @ParameterizedTest
    @CsvSource({"15,false", "16,true"})
    void preservesObservationGapBoundary(long gapSeconds, boolean expired) {
        VisitVerificationSession existing = session(2L, NOW.minusSeconds(gapSeconds), 1000.0);
        stubBatches(List.of(candidate(existing, Map.of())));
        if (expired) {
            when(sessions.expireForegroundPrefix(eq(1L), any(), eq(List.of(2L)), anyCollection(), any(), any(), any()))
                    .thenReturn(1);
            assertThatThrownBy(() -> service.startForeground(1L, request(10.0)))
                    .isInstanceOf(VisitorVerificationException.class);
            verify(sessions).expireForegroundPrefix(eq(1L), any(), eq(List.of(2L)), anyCollection(), any(),
                    eq(NOW), eq(NOW.minusSeconds(15)));
        } else {
            when(sessions.findByIdAndTouristUserIdForUpdate(2L, 1L)).thenReturn(Optional.of(existing));
            assertThat(service.startForeground(1L, request(10.0)).id()).isEqualTo(2L);
            verify(sessions, never()).expireForegroundPrefix(any(), any(), anyCollection(), anyCollection(), any(), any(), any());
        }
    }

    @ParameterizedTest
    @CsvSource({"HIDDEN,OPERATING", "VISIBLE,TEMPORARILY_CLOSED", "VISIBLE,PERMANENTLY_CLOSED"})
    void skipsUnavailablePlacesWithoutExpiringTheirSessions(PlaceDiscoveryStatus discovery, PlaceOperatingStatus operating) {
        VisitVerificationSession skipped = session(3L, NOW, 1000.0);
        VisitVerificationSession selected = session(2L, NOW.minusMillis(1), 1000.0);
        stubBatches(List.of(candidate(skipped, Map.of("discoveryStatus", discovery, "operatingStatus", operating)),
                candidate(selected, Map.of())));
        when(sessions.findByIdAndTouristUserIdForUpdate(2L, 1L)).thenReturn(Optional.of(selected));

        assertThat(service.startForeground(1L, request(10.0)).id()).isEqualTo(2L);

        verify(sessions, never()).expireForegroundPrefix(any(), any(), anyCollection(), anyCollection(), any(), any(), any());
    }

    @Test
    void roundsDatabaseCutoffUpWhenGapExceededByLessThanHalfAMicrosecond() {
        Instant requestNow = NOW.plusNanos(100);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(requestNow, ZoneOffset.UTC));
        VisitVerificationSession expired = session(2L, NOW.minusSeconds(15), 1000.0);
        stubBatches(List.of(candidate(expired, Map.of())));
        when(sessions.expireForegroundPrefix(eq(1L), any(), eq(List.of(2L)), anyCollection(), any(), any(), any()))
                .thenReturn(1);

        assertThatThrownBy(() -> service.startForeground(1L, request(10.0)))
                .isInstanceOf(VisitorVerificationException.class);

        verify(sessions).expireForegroundPrefix(eq(1L), any(), eq(List.of(2L)), anyCollection(),
                eq(VisitVerificationSessionStatus.EXPIRED), eq(requestNow), eq(NOW.minusSeconds(15).plusNanos(1000)));
    }

    @Test
    void skipsMissingLeftJoinPlace() {
        VisitVerificationSession skipped = session(3L, NOW, 1000.0);
        VisitVerificationSession selected = session(2L, NOW.minusMillis(1), 1000.0);
        Map<String, Object> missing = new HashMap<>();
        missing.put("operatingStatus", null);
        missing.put("discoveryStatus", null);
        missing.put("latitude", null);
        missing.put("longitude", null);
        stubBatches(List.of(candidate(skipped, missing), candidate(selected, Map.of())));
        when(sessions.findByIdAndTouristUserIdForUpdate(2L, 1L)).thenReturn(Optional.of(selected));

        assertThat(service.startForeground(1L, request(10.0)).id()).isEqualTo(2L);
    }

    @Test
    void includesExactRadiusBoundaryUsingExistingHaversine() {
        VisitVerificationSession selected = session(2L, NOW, 1000.0);
        double exactRadius = LocationCheckInService.distanceMeters(35.1801, 128.1078, 35.1802, 128.1078);
        ReflectionTestUtils.setField(selected, "requiredRadiusMeters", exactRadius);
        stubBatches(List.of(candidate(selected, Map.of("latitude", 35.1802))));
        when(sessions.findByIdAndTouristUserIdForUpdate(2L, 1L)).thenReturn(Optional.of(selected));

        assertThat(service.startForeground(1L, request(5.0)).id()).isEqualTo(2L);
    }

    @Test
    void keepsStoredRadiusAccuracyValidation() {
        VisitVerificationSession selected = session(2L, NOW, 10.0);
        stubBatches(List.of(candidate(selected, Map.of())));
        when(sessions.findByIdAndTouristUserIdForUpdate(2L, 1L)).thenReturn(Optional.of(selected));

        assertThatThrownBy(() -> service.startForeground(1L, request(15.0)))
                .isInstanceOfSatisfying(VisitorVerificationException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(VisitorVerificationErrorCode.LOCATION_TOO_INACCURATE));
        verify(sessions, never()).saveAndFlush(any());
    }

    @Test
    void stopsBeforeNewSessionWhenLockedCandidateChanged() {
        VisitVerificationSession selected = session(2L, NOW.minusSeconds(1), 1000.0);
        ForegroundRecoveryCandidate snapshot = candidate(selected, Map.of());
        selected.recordObservation(NOW, NOW, 0);
        stubBatches(List.of(snapshot));
        when(sessions.findByIdAndTouristUserIdForUpdate(2L, 1L)).thenReturn(Optional.of(selected));

        assertThatThrownBy(() -> service.startForeground(1L, request(10.0)))
                .isInstanceOf(ConcurrencyFailureException.class);
        verifyNoNewSessionPath();
    }

    @Test
    void propagatesLockConflictInsteadOfCreatingNewSession() {
        VisitVerificationSession selected = session(2L, NOW, 1000.0);
        stubBatches(List.of(candidate(selected, Map.of())));
        when(sessions.findByIdAndTouristUserIdForUpdate(2L, 1L))
                .thenThrow(new CannotAcquireLockException("snapshot conflict"));

        assertThatThrownBy(() -> service.startForeground(1L, request(10.0)))
                .isInstanceOf(CannotAcquireLockException.class);
        verifyNoNewSessionPath();
    }

    @Test
    void stopsBeforeNewSessionWhenConditionalExpiryDidNotMatch() {
        VisitVerificationSession expired = expiredSession(3L, NOW);
        VisitVerificationSession selected = session(2L, NOW.minusMillis(1), 1000.0);
        stubBatches(List.of(candidate(expired, Map.of()), candidate(selected, Map.of())));

        assertThatThrownBy(() -> service.startForeground(1L, request(10.0)))
                .isInstanceOf(ConcurrencyFailureException.class);
        verify(sessions, never()).findByIdAndTouristUserIdForUpdate(any(), any());
        verifyNoNewSessionPath();
    }

    private void verifyNoNewSessionPath() {
        verify(places, never()).findNearbyPlacesForVisitVerification(anyDouble(), anyDouble(), anyDouble(), any());
        verify(sessions, never()).saveAndFlush(any());
    }

    private void stubBatches(List<ForegroundRecoveryCandidate> candidates) {
        when(sessions.findForegroundRecoveryBatch(eq(1L), any(), anyCollection(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Instant cursorTime = invocation.getArgument(3);
                    Long cursorId = invocation.getArgument(4);
                    Pageable page = invocation.getArgument(5);
                    assertThat(page.getPageSize()).isEqualTo(50);
                    assertThat(page.getOffset()).isZero();
                    return candidates.stream()
                            .filter(candidate -> cursorTime == null
                                    || candidate.getLastVerifiedAt().isBefore(cursorTime)
                                    || candidate.getLastVerifiedAt().equals(cursorTime) && candidate.getId() < cursorId)
                            .limit(page.getPageSize()).toList();
                });
    }

    private VisitVerificationSession session(long id, Instant lastVerifiedAt, double radius) {
        VisitVerificationSession session = VisitVerificationSession.start(1L, 2L,
                LocalDate.ofInstant(NOW, ZoneId.of("Asia/Seoul")), lastVerifiedAt, lastVerifiedAt, 0,
                radius, Duration.ofSeconds(30), Duration.ofMinutes(5));
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }

    private VisitVerificationSession expiredSession(long id, Instant lastVerifiedAt) {
        VisitVerificationSession session = session(id, lastVerifiedAt, 1000.0);
        ReflectionTestUtils.setField(session, "expiresAt", NOW);
        return session;
    }

    private ForegroundRecoveryCandidate candidate(VisitVerificationSession session, Map<String, Object> overrides) {
        Map<String, Object> fields = new HashMap<>(Map.ofEntries(
                Map.entry("id", session.getId()), Map.entry("placeId", session.getPlaceId()),
                Map.entry("status", session.getStatus()), Map.entry("expiresAt", session.getExpiresAt()),
                Map.entry("lastVerifiedAt", session.getLastVerifiedAt()),
                Map.entry("requiredRadiusMeters", session.getRequiredRadiusMeters()),
                Map.entry("operatingStatus", PlaceOperatingStatus.OPERATING),
                Map.entry("discoveryStatus", PlaceDiscoveryStatus.VISIBLE),
                Map.entry("latitude", 35.1801), Map.entry("longitude", 128.1078)));
        fields.putAll(overrides);
        return new SpelAwareProxyProjectionFactory().createProjection(ForegroundRecoveryCandidate.class, fields);
    }

    private ForegroundVisitVerificationStartRequest request(double accuracy) {
        return new ForegroundVisitVerificationStartRequest(35.1801, 128.1078, accuracy, NOW);
    }
}
