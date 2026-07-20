package com.typenull.pingdom.moderation.application.query.dashboard;

import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.identity.domain.repository.CurrentBannedUserCounts;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.api.dto.dashboard.AdminDashboardSummaryResponse;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardQueryService {

    private final MapPlaceRepository mapPlaceRepository;
    private final MapImageRepository mapImageRepository;
    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;
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
}
