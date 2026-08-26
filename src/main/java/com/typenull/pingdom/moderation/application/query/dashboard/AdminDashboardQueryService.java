package com.typenull.pingdom.moderation.application.query.dashboard;

import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.identity.domain.repository.CurrentBannedUserCounts;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardMetricWindowResponse;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardOperationalMetricsResponse;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardPendingItem;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardPendingItemType;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardPendingItemsResponse;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardRecentActivitiesResponse;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardRecentPlaceItem;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardRecentPostItem;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardRecentReportItem;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardRecentUserSanctionItem;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardSummaryResponse;
import com.typenull.pingdom.moderation.application.support.AdminPlaceDuplicateResolver;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionAction;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionHistory;
import com.typenull.pingdom.moderation.infrastructure.persistence.UserSanctionHistoryRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceDuplicateQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Comparator;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardQueryService {

    private final MapPlaceRepository mapPlaceRepository;
    private final MapImageRepository mapImageRepository;
    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;
    private final UserSanctionHistoryRepository userSanctionHistoryRepository;
    private final MapPlaceDuplicateQueryRepository mapPlaceDuplicateQueryRepository;
    private final AdminPlaceDuplicateResolver adminPlaceDuplicateResolver;
    private final PlaceRegistrationApplicationRepository applicationRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getSummary() {
        LocalDateTime now = LocalDateTime.now(clock);
        CurrentBannedUserCounts bannedUserCounts = userRepository.countCurrentlyBannedByType(
                UserBanType.PERMANENT,
                UserBanType.TEMPORARY,
                now,
                null
        );

        return new AdminDashboardSummaryResponse(
                mapPlaceRepository.count(),
                mapImageRepository.count(),
                postReportRepository.countByStatus(PostReportStatus.PENDING),
                bannedUserCounts.total(),
                getOperationalMetrics(now)
        );
    }

    private AdminDashboardOperationalMetricsResponse getOperationalMetrics(LocalDateTime now) {
        LocalDateTime todayStartedAt = now.toLocalDate().atStartOfDay();
        LocalDateTime last7DaysStartedAt = todayStartedAt.minusDays(6);
        LocalDateTime expiringBanUntil = now.plusDays(7);

        return new AdminDashboardOperationalMetricsResponse(
                metricWindow("TODAY", todayStartedAt, now),
                metricWindow("LAST_7_DAYS", last7DaysStartedAt, now),
                countDuplicatePlaceGroups(),
                userRepository.countTemporaryBansExpiringUntil(
                        UserBanType.TEMPORARY,
                        now,
                        expiringBanUntil
                ),
                mapPlaceRepository.countMissingLocation(),
                expiringBanUntil,
                now
        );
    }

    private AdminDashboardMetricWindowResponse metricWindow(
            String period,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    ) {
        return new AdminDashboardMetricWindowResponse(
                period,
                startedAt,
                endedAt,
                mapPlaceRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanEqual(startedAt, endedAt),
                mapImageRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanEqual(startedAt, endedAt)
        );
    }

    private long countDuplicatePlaceGroups() {
        return adminPlaceDuplicateResolver
                .analyze(mapPlaceDuplicateQueryRepository.findPotentialDuplicatePlaces())
                .groups()
                .size();
    }

    @Transactional(readOnly = true)
    public AdminDashboardRecentActivitiesResponse getRecentActivities(int limit) {
        int safeLimit = normalizeLimit(limit);

        List<AdminDashboardRecentPlaceItem> places = mapPlaceRepository
                .findAll(PageRequest.of(0, safeLimit, Sort.by(Sort.Order.desc("id"))))
                .getContent()
                .stream()
                .map(this::toRecentPlaceItem)
                .toList();

        List<AdminDashboardRecentPostItem> posts = mapImageRepository
                .findAllBy(PageRequest.of(0, safeLimit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .getContent()
                .stream()
                .map(this::toRecentPostItem)
                .toList();

        List<AdminDashboardRecentReportItem> reports = postReportRepository
                .findRecentByStatusNot(
                        PostReportStatus.PENDING,
                        PageRequest.of(0, safeLimit, Sort.by(Sort.Order.desc("processedAt"), Sort.Order.desc("id")))
                )
                .stream()
                .map(this::toRecentReportItem)
                .toList();

        List<AdminDashboardRecentUserSanctionItem> userSanctions = userSanctionHistoryRepository
                .findByActionIn(
                        List.of(UserSanctionAction.APPLIED, UserSanctionAction.RELEASED),
                        PageRequest.of(0, safeLimit, Sort.by(Sort.Order.desc("processedAt"), Sort.Order.desc("id")))
                )
                .stream()
                .map(this::toRecentUserSanctionItem)
                .toList();

        return new AdminDashboardRecentActivitiesResponse(places, posts, reports, userSanctions);
    }

    @Transactional(readOnly = true)
    public AdminDashboardPendingItemsResponse getPendingItems(int limit) {
        int safeLimit = normalizeLimit(limit);
        PageRequest pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        List<AdminDashboardPendingItem> reportItems = postReportRepository
                .findRecentByStatus(
                        PostReportStatus.PENDING,
                        pageable
                )
                .stream()
                .map(this::toPendingItem)
                .toList();
        List<AdminDashboardPendingItem> applicationItems = applicationRepository
                .findAllByStatus(
                        PlaceRegistrationStatus.PENDING,
                        pageable
                )
                .getContent()
                .stream()
                .map(this::toPendingItem)
                .toList();
        List<AdminDashboardPendingItem> items = Stream.concat(reportItems.stream(), applicationItems.stream())
                .sorted(Comparator.comparing(AdminDashboardPendingItem::createdAt).reversed()
                        .thenComparing(AdminDashboardPendingItem::targetId, Comparator.reverseOrder()))
                .limit(safeLimit)
                .toList();

        return new AdminDashboardPendingItemsResponse(
                items,
                postReportRepository.countByStatus(PostReportStatus.PENDING)
                        + applicationRepository.countByStatus(PlaceRegistrationStatus.PENDING)
        );
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 50));
    }

    private AdminDashboardRecentPlaceItem toRecentPlaceItem(MapPlace place) {
        return new AdminDashboardRecentPlaceItem(
                place.getId(),
                place.getName(),
                place.getAddress(),
                place.getUserId(),
                place.getRegistrant(),
                place.getCreatedAt()
        );
    }

    private AdminDashboardRecentPostItem toRecentPostItem(MapImage post) {
        MapPlace place = post.getMapPlace();
        return new AdminDashboardRecentPostItem(
                post.getId(),
                post.getTitle(),
                post.getUserId(),
                post.getUsername(),
                place == null ? null : place.getId(),
                place == null ? null : place.getName(),
                post.getCreatedAt()
        );
    }

    private AdminDashboardRecentReportItem toRecentReportItem(PostReport report) {
        return new AdminDashboardRecentReportItem(
                report.getId(),
                report.getReportedImageId(),
                reportTitle(report),
                report.getStatus(),
                report.getProcessedAt(),
                report.getCreatedAt()
        );
    }

    private AdminDashboardRecentUserSanctionItem toRecentUserSanctionItem(UserSanctionHistory history) {
        return new AdminDashboardRecentUserSanctionItem(
                history.getId(),
                history.getTargetUserId(),
                history.getTargetUsername(),
                history.getAction(),
                history.getBanType(),
                history.getReason(),
                history.getProcessedAt()
        );
    }

    private AdminDashboardPendingItem toPendingItem(PostReport report) {
        return new AdminDashboardPendingItem(
                AdminDashboardPendingItemType.POST_REPORT,
                report.getId(),
                report.getId(),
                report.getReportedImageId(),
                reportTitle(report),
                report.getStatus().name(),
                report.getCreatedAt(),
                null
        );
    }

    private AdminDashboardPendingItem toPendingItem(PlaceRegistrationApplication application) {
        return new AdminDashboardPendingItem(
                AdminDashboardPendingItemType.MERCHANT_PLACE_APPLICATION,
                application.getId(),
                null,
                null,
                application.getBusinessName() + " · " + application.getPlaceName(),
                application.getStatus().name(),
                application.getCreatedAt(),
                "/admin/merchant-place-applications/" + application.getId()
        );
    }

    private String reportTitle(PostReport report) {
        MapImage post = report.getMapImage();
        if (post != null && post.getTitle() != null && !post.getTitle().isBlank()) {
            return post.getTitle();
        }
        return report.getReason();
    }
}
