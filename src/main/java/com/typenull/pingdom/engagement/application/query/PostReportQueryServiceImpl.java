package com.typenull.pingdom.engagement.application.query;

import com.typenull.pingdom.engagement.api.dto.report.MyPostReportItem;
import com.typenull.pingdom.engagement.api.dto.report.MyPostReportResponse;
import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.post.domain.MapImage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostReportQueryServiceImpl implements PostReportQueryService {

    private static final int MIN_PAGE = 1;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final PostReportRepository postReportRepository;

    @Override
    @Transactional(readOnly = true)
    public MyPostReportResponse listMyReports(Long userId, int page, int limit) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        int safePage = Math.max(page, MIN_PAGE);
        int safeLimit = Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));

        Page<PostReport> reportPage = postReportRepository.findByReporterUserIdOrderByIdDesc(
                userId,
                PageRequest.of(safePage - MIN_PAGE, safeLimit)
        );
        List<MyPostReportItem> reports = reportPage.getContent().stream()
                .map(this::toItem)
                .toList();

        return MyPostReportResponse.of(
                reports,
                safePage,
                safeLimit,
                reportPage.getTotalElements(),
                reportPage.getTotalPages()
        );
    }

    private MyPostReportItem toItem(PostReport report) {
        MapImage mapImage = report.getMapImage();
        MapPlace mapPlace = mapImage == null ? null : mapImage.getMapPlace();
        return new MyPostReportItem(
                report.getId(),
                report.getReportedImageId(),
                mapImage == null ? null : mapImage.getTitle(),
                mapImage == null ? report.getReportedImageUrl() : mapImage.getImageUrl(),
                mapImage == null ? null : mapImage.getThumbnailUrl(),
                mapImage == null ? null : mapImage.getDescription(),
                mapImage == null ? null : mapImage.getUserId(),
                mapImage == null ? null : mapImage.getUsername(),
                mapImage == null ? null : mapImage.getCreatedAt(),
                mapPlace == null ? null : mapPlace.getId(),
                mapPlace == null ? null : mapPlace.getName(),
                report.getReason(),
                report.getStatus()
        );
    }
}
