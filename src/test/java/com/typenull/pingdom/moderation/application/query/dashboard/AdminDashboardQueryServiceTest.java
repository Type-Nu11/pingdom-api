package com.typenull.pingdom.moderation.application.query.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.identity.domain.repository.CurrentBannedUserCounts;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardSummaryResponse;
import com.typenull.pingdom.moderation.application.support.AdminPlaceDuplicateResolver;
import com.typenull.pingdom.moderation.infrastructure.persistence.UserSanctionHistoryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceDuplicateQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminDashboardQueryServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-20T12:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private MapPlaceRepository mapPlaceRepository;
    @Mock
    private MapImageRepository mapImageRepository;
    @Mock
    private PostReportRepository postReportRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSanctionHistoryRepository userSanctionHistoryRepository;
    @Mock
    private MapPlaceDuplicateQueryRepository mapPlaceDuplicateQueryRepository;

    private AdminDashboardQueryService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardQueryService(
                mapPlaceRepository,
                mapImageRepository,
                postReportRepository,
                userRepository,
                userSanctionHistoryRepository,
                mapPlaceDuplicateQueryRepository,
                new AdminPlaceDuplicateResolver(),
                FIXED_CLOCK
        );
    }

    @Test
    void getSummaryReturnsAllOperationalCounts() {
        when(mapPlaceRepository.count()).thenReturn(44L);
        when(mapImageRepository.count()).thenReturn(58L);
        when(postReportRepository.countByStatus(PostReportStatus.PENDING)).thenReturn(5L);
        when(userRepository.countCurrentlyBannedByType(
                UserBanType.PERMANENT,
                UserBanType.TEMPORARY,
                LocalDateTime.of(2026, 7, 20, 12, 0),
                null
        )).thenReturn(new CurrentBannedUserCounts(6L, 4L, 2L));
        when(mapPlaceRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanEqual(
                LocalDateTime.of(2026, 7, 20, 0, 0),
                LocalDateTime.of(2026, 7, 20, 12, 0)
        )).thenReturn(3L);
        when(mapImageRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanEqual(
                LocalDateTime.of(2026, 7, 20, 0, 0),
                LocalDateTime.of(2026, 7, 20, 12, 0)
        )).thenReturn(7L);
        when(mapPlaceRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanEqual(
                LocalDateTime.of(2026, 7, 14, 0, 0),
                LocalDateTime.of(2026, 7, 20, 12, 0)
        )).thenReturn(12L);
        when(mapImageRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanEqual(
                LocalDateTime.of(2026, 7, 14, 0, 0),
                LocalDateTime.of(2026, 7, 20, 12, 0)
        )).thenReturn(31L);
        when(userRepository.countTemporaryBansExpiringUntil(
                UserBanType.TEMPORARY,
                LocalDateTime.of(2026, 7, 20, 12, 0),
                LocalDateTime.of(2026, 7, 27, 12, 0)
        )).thenReturn(4L);
        when(mapPlaceRepository.countMissingLocation()).thenReturn(1L);

        AdminDashboardSummaryResponse response = service.getSummary();

        assertEquals(44L, response.placeCount());
        assertEquals(58L, response.postCount());
        assertEquals(5L, response.pendingReportCount());
        assertEquals(6L, response.bannedUserCount());
        assertEquals(3L, response.operationalMetrics().today().placeRegistrationCount());
        assertEquals(7L, response.operationalMetrics().today().postRegistrationCount());
        assertEquals(12L, response.operationalMetrics().last7Days().placeRegistrationCount());
        assertEquals(31L, response.operationalMetrics().last7Days().postRegistrationCount());
        assertEquals(0L, response.operationalMetrics().duplicatePlaceGroupCount());
        assertEquals(4L, response.operationalMetrics().expiringBannedUserCount());
        assertEquals(1L, response.operationalMetrics().missingLocationPlaceCount());
        assertEquals(LocalDateTime.of(2026, 7, 27, 12, 0), response.operationalMetrics().expiringBanUntil());
        assertEquals(LocalDateTime.of(2026, 7, 20, 12, 0), response.operationalMetrics().collectedAt());
    }

    @Test
    void getSummaryReturnsZerosWhenNoDataExists() {
        when(userRepository.countCurrentlyBannedByType(
                UserBanType.PERMANENT,
                UserBanType.TEMPORARY,
                LocalDateTime.of(2026, 7, 20, 12, 0),
                null
        )).thenReturn(new CurrentBannedUserCounts(0L, 0L, 0L));

        AdminDashboardSummaryResponse response = service.getSummary();

        assertEquals(0L, response.placeCount());
        assertEquals(0L, response.postCount());
        assertEquals(0L, response.pendingReportCount());
        assertEquals(0L, response.bannedUserCount());
        assertEquals(0L, response.operationalMetrics().today().placeRegistrationCount());
        assertEquals(0L, response.operationalMetrics().today().postRegistrationCount());
        assertEquals(0L, response.operationalMetrics().last7Days().placeRegistrationCount());
        assertEquals(0L, response.operationalMetrics().last7Days().postRegistrationCount());
        assertEquals(0L, response.operationalMetrics().duplicatePlaceGroupCount());
        assertEquals(0L, response.operationalMetrics().expiringBannedUserCount());
        assertEquals(0L, response.operationalMetrics().missingLocationPlaceCount());
        verify(postReportRepository).countByStatus(PostReportStatus.PENDING);
    }
}
