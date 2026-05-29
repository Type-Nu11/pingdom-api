package com.typenull.pingdom.domain.admin.service.impl;

import com.typenull.pingdom.domain.admin.dto.picture.AdminPictureItem;
import com.typenull.pingdom.domain.admin.dto.picture.AdminPictureReportItem;
import com.typenull.pingdom.domain.admin.dto.picture.AdminPictureResponse;
import com.typenull.pingdom.domain.admin.enums.SortParam;
import com.typenull.pingdom.domain.admin.service.AdminPictureQueryService;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.domain.PictureReport;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import com.typenull.pingdom.domain.map.repository.PictureReportRepository;
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
public class AdminPictureQueryServiceImpl implements AdminPictureQueryService {

    private final MapImageRepository mapImageRepository;
    private final PictureReportRepository pictureReportRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminPictureResponse listPictures(int limit, int page, SortParam sortParam) {
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

        Map<Long, List<AdminPictureReportItem>> reportsByImageId = getReportsByImageId(mapImagePage.getContent());

        List<AdminPictureItem> pictures = mapImagePage.getContent()
                .stream()
                .map(mapImage -> toItem(mapImage, reportsByImageId.getOrDefault(mapImage.getId(), List.of())))
                .toList();

        return AdminPictureResponse.of(
                pictures,
                page,
                safeLimit,
                mapImagePage.getTotalElements(),     // totalCount
                mapImagePage.getTotalPages()        // totalPages
        );
    }

    private Map<Long, List<AdminPictureReportItem>> getReportsByImageId(List<MapImage> mapImages) {
        if (mapImages.isEmpty()) {
            return Map.of();
        }

        return pictureReportRepository.findAllByMapImage_IdInOrderByIdDesc(
                        mapImages.stream().map(MapImage::getId).toList()
                ).stream()
                .collect(groupingBy(
                        pictureReport -> pictureReport.getMapImage().getId(),
                        java.util.stream.Collectors.mapping(this::toReportItem, java.util.stream.Collectors.toList())
                ));
    }

    private AdminPictureItem toItem(MapImage mapImage, List<AdminPictureReportItem> reports) {
        return new AdminPictureItem(
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

    private AdminPictureReportItem toReportItem(PictureReport pictureReport) {
        return new AdminPictureReportItem(
                pictureReport.getId(),
                pictureReport.getReporterUserId(),
                pictureReport.getReporterUsername(),
                pictureReport.getReason(),
                pictureReport.getStatus(),
                pictureReport.getProcessedAt()
        );
    }
}
