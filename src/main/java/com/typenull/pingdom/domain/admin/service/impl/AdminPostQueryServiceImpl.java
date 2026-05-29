package com.typenull.pingdom.domain.admin.service.impl;

import com.typenull.pingdom.domain.admin.dto.post.AdminPostItem;
import com.typenull.pingdom.domain.admin.dto.post.AdminPostReportItem;
import com.typenull.pingdom.domain.admin.dto.post.AdminPostResponse;
import com.typenull.pingdom.domain.admin.enums.SortParam;
import com.typenull.pingdom.domain.admin.service.AdminPostQueryService;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.domain.PostReport;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import com.typenull.pingdom.domain.map.repository.PostReportRepository;
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
    public AdminPostResponse listPosts(int limit, int page, SortParam sortParam) {
        // 리미트 값을 1~100사이로 고정
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int targetPage = Math.max(page - 1, 0);
        SortParam safeSortParam = sortParam == null ? SortParam.LATEST : sortParam;

        Sort sort = switch (safeSortParam) {
            case OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt");
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case MOST_LIKED -> Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };

        Page<MapImage> mapImagePage = mapImageRepository.findAllBy(
                PageRequest.of(targetPage, safeLimit, sort)
        );

        Map<Long, List<AdminPostReportItem>> reportsByImageId = getReportsByImageId(mapImagePage.getContent());

        List<AdminPostItem> posts = mapImagePage.getContent()
                .stream()
                .map(mapImage -> toItem(mapImage, reportsByImageId.getOrDefault(mapImage.getId(), List.of())))
                .toList();

        return AdminPostResponse.of(
                posts,
                page,
                safeLimit,
                mapImagePage.getTotalElements(),     // totalCount
                mapImagePage.getTotalPages()        // totalPages
        );
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
                mapImage.getImageUrl(), // thumbnailUrl
                mapImage.getImageUrl(), // imageUrl
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
}
