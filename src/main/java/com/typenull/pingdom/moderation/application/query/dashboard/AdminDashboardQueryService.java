package com.typenull.pingdom.moderation.application.query.dashboard;

import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.identity.domain.repository.CurrentBannedUserCounts;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardPendingItem;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardPendingItemsResponse;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardRecentActivitiesResponse;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardRecentPlaceItem;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardRecentPostItem;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardRecentReportItem;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardRecentUserSanctionItem;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardSummaryResponse;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionAction;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionHistory;
import com.typenull.pingdom.moderation.infrastructure.persistence.UserSanctionHistoryRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
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
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getSummary() {
        CurrentBannedUserCounts bannedUserCounts = userRepository.countCurrentlyBannedByType(
                UserBanType.PERMANENT,
                UserBanType.TEMPORARY,
                LocalDateTime.now(clock),
                null
        );

        return new AdminDashboardSummaryResponse(
                mapPlaceRepository.count(),
                mapImageRepository.count(),
                postReportRepository.countByStatus(PostReportStatus.PENDING),
                bannedUserCounts.total()
        );
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
        List<AdminDashboardPendingItem> items = postReportRepository
                .findRecentByStatus(
                        PostReportStatus.PENDING,
                        PageRequest.of(0, safeLimit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
                )
                .stream()
                .map(this::toPendingItem)
                .toList();

        return new AdminDashboardPendingItemsResponse(items);
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
                place.getRegistrant()
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
                "POST_REPORT",
                report.getId(),
                reportTitle(report),
                report.getStatus().name(),
                report.getCreatedAt()
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
