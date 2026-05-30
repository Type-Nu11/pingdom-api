package com.typenull.pingdom.domain.map.service;

import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.domain.PostReport;
import com.typenull.pingdom.domain.map.dto.PostReportRequest;
import com.typenull.pingdom.domain.map.exception.MapErrorCode;
import com.typenull.pingdom.domain.map.exception.MapException;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import com.typenull.pingdom.domain.map.repository.PostReportRepository;
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
