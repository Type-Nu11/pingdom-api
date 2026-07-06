package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.moderation.api.dto.post.AdminPostItem;
import com.typenull.pingdom.moderation.api.dto.post.AdminPostReportItem;
import com.typenull.pingdom.moderation.api.dto.post.AdminPostReviewCounts;
import com.typenull.pingdom.moderation.api.dto.post.AdminPostResponse;
import com.typenull.pingdom.moderation.domain.AdminPostReviewStatus;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static java.util.stream.Collectors.groupingBy;

@Service
@RequiredArgsConstructor
public class AdminPostQueryServiceImpl implements AdminPostQueryService {

    private final MapImageRepository mapImageRepository;
    private final PostReportRepository postReportRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminPostResponse listPosts(
            int page,
            int limit,
            SortParam sortParam,
            String keyword,
            AdminPostReviewStatus reviewStatus,
            PostReportStatus reportStatus
    ) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int targetPage = safePage - 1;
        SortParam safeSortParam = sortParam == null ? SortParam.LATEST : sortParam;
        AdminPostReviewStatus safeReviewStatus = reviewStatus == null ? AdminPostReviewStatus.ALL : reviewStatus;
        String safeKeyword = keyword == null ? "" : keyword.trim();
        Long numericKeyword = parseLongKeyword(safeKeyword);

        Sort sort = switch (safeSortParam) {
            case OLDEST -> Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
            case LATEST -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case MOST_LIKED -> Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };

        PageRequest pageable = PageRequest.of(targetPage, safeLimit, sort);
        Page<MapImage> mapImagePage = loadAdminPostPage(safeKeyword, numericKeyword, safeReviewStatus, reportStatus, pageable);
        AdminPostReviewCounts counts = countAdminPostsByReviewStatus(safeKeyword, numericKeyword);

        Map<Long, List<AdminPostReportItem>> reportsByImageId = getReportsByImageId(mapImagePage.getContent());

        List<AdminPostItem> posts = mapImagePage.getContent()
                .stream()
                .map(mapImage -> toItem(mapImage, reportsByImageId.getOrDefault(mapImage.getId(), List.of())))
                .toList();

        return AdminPostResponse.of(
                posts,
                safePage,
                safeLimit,
                mapImagePage.getTotalElements(),
                mapImagePage.getTotalPages(),
                counts
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPostItem getPost(Long postId) {
        MapImage mapImage = mapImageRepository.findById(postId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.POST_NOT_FOUND));

        List<AdminPostReportItem> reports = getReportsByImageId(List.of(mapImage))
                .getOrDefault(mapImage.getId(), List.of());

        return toItem(mapImage, reports);
    }

    private Map<Long, List<AdminPostReportItem>> getReportsByImageId(List<MapImage> mapImages) {
        if (mapImages.isEmpty()) {
            return Map.of();
        }

        return postReportRepository.findAllByMapImage_IdInOrderByIdDesc(
                        mapImages.stream().map(MapImage::getId).toList()
                ).stream()
                .collect(groupingBy(
                        PostReport::getReportedImageId,
                        java.util.stream.Collectors.mapping(this::toReportItem, java.util.stream.Collectors.toList())
                ));
    }

    private AdminPostItem toItem(MapImage mapImage, List<AdminPostReportItem> reports) {
        return new AdminPostItem(
                mapImage.getId(),
                mapImage.getTitle(),
                mapImage.getImageUrl(),
                mapImage.getThumbnailUrl(),
                mapImage.getUserId(),
                mapImage.getUsername(),
                mapImage.getCreatedAt(),
                mapImage.getDescription(),
                mapImage.getLikeCount(),
                mapImage.getMapPlace() != null ? mapImage.getMapPlace().getName() : null,
                mapImage.getVisibilityStatus(),
                mapImage.getHiddenAt(),
                mapImage.getHiddenReason(),
                reports
        );
    }

    private AdminPostReportItem toReportItem(PostReport postReport) {
        return new AdminPostReportItem(
                postReport.getId(),
                postReport.getReporterUserId(),
                postReport.getReporterUsername(),
                postReport.getReason(),
                postReport.getStatus(),
                postReport.getCreatedAt(),
                postReport.getProcessedAt()
        );
    }

    private Long parseLongKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(keyword);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Page<MapImage> loadAdminPostPage(
            String keyword,
            Long numericKeyword,
            AdminPostReviewStatus reviewStatus,
            PostReportStatus reportStatus,
            Pageable pageable
    ) {
        if (keyword.isBlank() && reportStatus == null && reviewStatus == AdminPostReviewStatus.ALL) {
            return mapImageRepository.findAllBy(pageable);
        }

        return mapImageRepository.searchAdminPosts(keyword, numericKeyword, reviewStatus.name(), reportStatus, pageable);
    }

    private AdminPostReviewCounts countAdminPostsByReviewStatus(String keyword, Long numericKeyword) {
        return new AdminPostReviewCounts(
                mapImageRepository.countAdminPostsByReviewStatus(keyword, numericKeyword, AdminPostReviewStatus.ALL.name()),
                mapImageRepository.countAdminPostsByReviewStatus(keyword, numericKeyword, AdminPostReviewStatus.PENDING.name()),
                mapImageRepository.countAdminPostsByReviewStatus(keyword, numericKeyword, AdminPostReviewStatus.PROCESSED.name()),
                mapImageRepository.countAdminPostsByReviewStatus(keyword, numericKeyword, AdminPostReviewStatus.NORMAL.name())
        );
    }
}
