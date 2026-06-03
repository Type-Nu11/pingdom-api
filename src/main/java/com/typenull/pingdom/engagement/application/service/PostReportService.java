package com.typenull.pingdom.engagement.application.service;

import com.typenull.pingdom.engagement.api.dto.PostReportRequest;
import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.domain.repository.PostReportRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostReportService {

    private final MapImageRepository mapImageRepository;
    private final PostReportRepository postReportRepository;

    @Transactional
    public void report(Long imageId, Long reporterUserId, String reporterUsername, PostReportRequest request) {
        MapImage mapImage = mapImageRepository.findById(imageId)
                .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));

        if (postReportRepository.existsByReporterUserIdAndMapImage_Id(reporterUserId, imageId)) {
            throw new MapException(MapErrorCode.ALREADY_REPORTED_IMAGE);
        }

        PostReport postReport = PostReport.builder()
                .reporterUserId(reporterUserId)
                .reporterUsername(reporterUsername)
                .reportedImageId(mapImage.getId())
                .reportedUserId(mapImage.getUserId())
                .reportedImageUrl(mapImage.getImageUrl())
                .mapImage(mapImage)
                .reason(request.reason())
                .build();

        try {
            postReportRepository.saveAndFlush(postReport);
        } catch (DataIntegrityViolationException exception) {
            throw new MapException(MapErrorCode.ALREADY_REPORTED_IMAGE);
        }
    }
}
