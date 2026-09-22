package com.typenull.pingdom.engagement.application.service;

import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.event.PostReportCreatedEvent;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 게시글 신고 중복·권한 정책을 검증하고 신고 상태를 생성합니다. */
@Service
@RequiredArgsConstructor
public class PostReportService {

    private final MapImageRepository mapImageRepository;
    private final PostReportRepository postReportRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ReportPolicyService reportPolicyService;
    private final Clock clock;

    /**
     * 공개 이미지에 대한 신고를 저장하고 동기 이벤트를 발행한 뒤 신고 통계와 자동 숨김 조건을 평가합니다.
     * 현재 구현은 saveAndFlush 및 이벤트 발행 중 DataIntegrityViolationException을 모두 중복 신고로 변환합니다.
     */
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
            eventPublisher.publishEvent(new PostReportCreatedEvent(postReport.getId()));
        } catch (DataIntegrityViolationException exception) {
            throw new MapException(MapErrorCode.ALREADY_REPORTED_IMAGE);
        }
        reportPolicyService.recordSubmitted(reporterUserId, reporterUsername);
        reportPolicyService.autoHideIfNeeded(mapImage, now);
    }
}
