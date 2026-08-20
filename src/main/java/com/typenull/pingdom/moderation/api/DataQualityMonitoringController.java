package com.typenull.pingdom.moderation.api;

import com.typenull.pingdom.shared.security.annotation.AdminOnly;
import com.typenull.pingdom.moderation.api.dto.DataQualityIssueResponse;
import com.typenull.pingdom.shared.quality.DataQualityMonitoringService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public List<DataQualityIssueResponse> openIssues() {
        return service.openIssues().stream().map(DataQualityIssueResponse::from).toList();
    }
}
