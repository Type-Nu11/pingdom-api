package com.typenull.pingdom.moderation.api.report;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.moderation.api.dto.storage.AdminS3OrphanObjectReportResponse;
import com.typenull.pingdom.moderation.application.query.storage.AdminS3ObjectReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/s3")
@RequiredArgsConstructor
@AdminOnly
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminS3ObjectReportController {

    private final AdminS3ObjectReportService adminS3ObjectReportService;

    @GetMapping("/orphan-objects")
    @Operation(
            summary = "S3 orphan 객체 dry-run 리포트",
            description = "DB에 등록된 게시글 원본/썸네일 S3 key와 S3 객체 목록을 비교해 DB에서 사용 중이지 않은 객체를 조회합니다. 실제 삭제는 수행하지 않습니다."
    )
    public AdminS3OrphanObjectReportResponse reportOrphanObjects(
            @Parameter(description = "조회할 S3 key prefix", example = "map/")
            @RequestParam(defaultValue = "map/") String prefix,
            @Parameter(description = "최대 스캔 객체 수. 1~10000 범위로 보정됩니다.", example = "1000")
            @RequestParam(defaultValue = "1000") Integer limit
    ) {
        return adminS3ObjectReportService.reportOrphanObjects(prefix, limit);
    }
}
