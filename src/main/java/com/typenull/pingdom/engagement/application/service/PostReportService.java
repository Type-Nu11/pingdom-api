package com.typenull.pingdom.engagement.application.service;

import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostReportService {

    private final MapImageRepository mapImageRepository;
    private final PostReportRepository postReportRepository;
    private final ReportPolicyService reportPolicyService;
    private final Clock clock;

    @Transactional
    public void report(Long imageId, Long reporterUserId, String reporterUsername, String reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        reportPolicyService.validateCanReport(reporterUserId, now);

        MapImage mapImage = mapImageRepository.findById(imageId)
                .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));

        if (!mapImage.isVisible()) {
            throw new MapException(MapErrorCode.IMAGE_NOT_FOUND);
        }

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
                .reason(reason)
                .build();

        try {
            postReportRepository.saveAndFlush(postReport);
        } catch (DataIntegrityViolationException exception) {
            throw new MapException(MapErrorCode.ALREADY_REPORTED_IMAGE);
        }
        reportPolicyService.recordSubmitted(reporterUserId, reporterUsername);
        reportPolicyService.autoHideIfNeeded(mapImage, now);
    }
}
