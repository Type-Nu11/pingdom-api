package com.typenull.pingdom.moderation.application.query.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardPendingItemType;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardPendingItemsResponse;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.identity.domain.repository.CurrentBannedUserCounts;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardSummaryResponse;
import com.typenull.pingdom.moderation.application.support.AdminPlaceDuplicateResolver;
import com.typenull.pingdom.moderation.infrastructure.persistence.UserSanctionHistoryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceDuplicateQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

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
    @Mock
    private PlaceRegistrationApplicationRepository applicationRepository;

    private AdminDashboardQueryService service;

    /**
     * UTC 기준 오늘·최근 7일·향후 7일의 집계 경계를 고정하고 운영 저장소 결과를 조합할 대시보드 서비스를 구성한다.
     */
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
                applicationRepository,
                FIXED_CLOCK
        );
    }

    /**
     * 전체 장소·게시글·미처리 신고·정지 사용자와 오늘·최근 7일 등록 수를 요약에 반영하는지 검증한다.
     * 위치 누락·중복 그룹·만료 예정 정지 수 및 집계·만료 기준 시각도 확인한다.
     */
    @Test
    void returnsDashboardOperationalCounts() {
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

    /**
     * 집계 대상이 없으면 요약의 모든 운영 수치가 0이고 대기 신고 수 조회를 수행하는지 검증한다.
     */
    @Test
    void returnsEmptyDashboardCounts() {
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

    /**
     * 대기 신고 항목의 targetId·reportId는 신고 ID로, postId는 신고된 게시글 ID로 구분하고 전체 수를 반영하는지 검증한다.
     */
    @Test
    void separatesPendingReportAndPostIds() {
        PostReport report = PostReport.builder()
                .id(30L)
                .reportedImageId(22L)
                .reportedUserId(7L)
                .reporterUserId(8L)
                .reporterUsername("tourist")
                .reportedImageUrl("https://cdn.pingdom.test/post.jpg")
                .reason("잘못된 장소 정보")
                .status(PostReportStatus.PENDING)
                .createdAt(LocalDateTime.of(2026, 7, 21, 15, 40))
                .build();
        when(postReportRepository.findRecentByStatus(
                org.mockito.ArgumentMatchers.eq(PostReportStatus.PENDING),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of(report));
        when(applicationRepository.findAllByStatus(
                org.mockito.ArgumentMatchers.eq(PlaceRegistrationStatus.PENDING),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(new PageImpl<>(List.of()));
        when(postReportRepository.countByStatus(PostReportStatus.PENDING)).thenReturn(1L);

        AdminDashboardPendingItemsResponse response = service.getPendingItems(10);

        assertEquals(1, response.items().size());
        assertEquals(AdminDashboardPendingItemType.POST_REPORT, response.items().get(0).type());
        assertEquals(30L, response.items().get(0).targetId());
        assertEquals(30L, response.items().get(0).reportId());
        assertEquals(22L, response.items().get(0).postId());
        assertEquals(1L, response.totalCount());
    }

    /**
     * 대기 점주 장소 신청을 항목으로 변환하면 신청 유형과 관리자 상세 이동 경로, 전체 수를 제공하는지 검증한다.
     */
    @Test
    void linksPendingMerchantApplicationDetail() {
        PlaceRegistrationApplication application = org.mockito.Mockito.mock(PlaceRegistrationApplication.class);
        when(application.getId()).thenReturn(12L);
        when(application.getBusinessName()).thenReturn("핑덤 카페");
        when(application.getPlaceName()).thenReturn("시청점");
        when(application.getStatus()).thenReturn(PlaceRegistrationStatus.PENDING);
        when(application.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 7, 21, 15, 50));
        when(postReportRepository.findRecentByStatus(
                org.mockito.ArgumentMatchers.eq(PostReportStatus.PENDING),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of());
        when(applicationRepository.findAllByStatus(
                org.mockito.ArgumentMatchers.eq(PlaceRegistrationStatus.PENDING),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(new PageImpl<>(List.of(application)));
        when(applicationRepository.countByStatus(PlaceRegistrationStatus.PENDING)).thenReturn(1L);

        AdminDashboardPendingItemsResponse response = service.getPendingItems(10);

        assertEquals(1, response.items().size());
        assertEquals(AdminDashboardPendingItemType.MERCHANT_PLACE_APPLICATION, response.items().get(0).type());
        assertEquals("/admin/merchant-place-applications/12", response.items().get(0).navigationPath());
        assertEquals(1L, response.totalCount());
    }
}
