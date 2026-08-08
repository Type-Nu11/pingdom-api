package com.typenull.pingdom.moderation.api;

import com.typenull.pingdom.moderation.api.dto.DataQualityIssueResponse;
import com.typenull.pingdom.shared.quality.DataQualityMonitoringService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/data-quality/issues")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DataQualityMonitoringController {
    private final DataQualityMonitoringService service;

    @GetMapping
    public List<DataQualityIssueResponse> openIssues() {
        return service.openIssues().stream().map(DataQualityIssueResponse::from).toList();
    }
}
