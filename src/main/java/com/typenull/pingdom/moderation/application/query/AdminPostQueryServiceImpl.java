package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.moderation.api.dto.post.AdminPostItem;
import com.typenull.pingdom.moderation.api.dto.post.AdminPostReportItem;
import com.typenull.pingdom.moderation.api.dto.post.AdminPostResponse;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    public AdminPostResponse listPosts(int page, int limit, SortParam sortParam, String keyword) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int targetPage = safePage - 1;
        SortParam safeSortParam = sortParam == null ? SortParam.LATEST : sortParam;
        String safeKeyword = keyword == null ? "" : keyword.trim();
        Long numericKeyword = parseLongKeyword(safeKeyword);

        Sort sort = switch (safeSortParam) {
            case OLDEST -> Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
            case LATEST -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case MOST_LIKED -> Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };

        Page<MapImage> mapImagePage = mapImageRepository.searchAdminPosts(
                safeKeyword,
                numericKeyword,
                PageRequest.of(targetPage, safeLimit, sort)
        );

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
                mapImagePage.getTotalPages()
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
                mapImage.getUserId(),
                mapImage.getUsername(),
                mapImage.getCreatedAt(),
                mapImage.getDescription(),
                mapImage.getLikeCount(),
                mapImage.getMapPlace() != null ? mapImage.getMapPlace().getName() : null,
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
}
