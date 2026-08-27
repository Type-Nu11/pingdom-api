package com.typenull.pingdom.moderation.api;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.moderation.api.dto.DataQualityIssueResponse;
import com.typenull.pingdom.moderation.api.dto.DataQualityIssuePageResponse;
import com.typenull.pingdom.shared.quality.DataQualityMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/admin/data-quality/issues")
@RequiredArgsConstructor
@AdminOnly
public class DataQualityMonitoringController {
    private final DataQualityMonitoringService service;

    @GetMapping
    @Operation(summary = "데이터 품질 이슈 목록 조회")
    public DataQualityIssuePageResponse openIssues(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        var result = service.openIssues(page, limit);
        return new DataQualityIssuePageResponse(
                result.getContent().stream().map(DataQualityIssueResponse::from).toList(),
                result.getNumber() + 1, result.getSize(), result.getTotalElements(), result.getTotalPages(), result.hasNext()
        );
    }
}
