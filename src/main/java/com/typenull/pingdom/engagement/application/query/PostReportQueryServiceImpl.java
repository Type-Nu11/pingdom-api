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

/**
 * 요청자의 신고 이력에 현재 게시글·장소 정보를 결합합니다.
 * 원본 게시글 연결이 삭제된 경우 신고 당시 이미지 ID·URL은 유지하고 복구할 수 없는 표시 필드는 null로 반환합니다.
 */
@Service
@RequiredArgsConstructor
public class PostReportQueryServiceImpl implements PostReportQueryService {

    private static final int MIN_PAGE = 1;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final PostReportRepository postReportRepository;

    /**
     * 사용자 ID가 없는 호출을 거부하고 본인 신고만 ID 내림차순으로 조회한다.
     * 페이지는 최소 1, 크기는 1~100으로 보정하며 원본 게시글이 삭제되어도 신고 당시 이미지 ID·URL을 이용해 이력을 반환한다.
     */
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
