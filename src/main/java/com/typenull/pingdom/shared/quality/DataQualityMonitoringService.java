package com.typenull.pingdom.shared.quality;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataQualityMonitoringService {
    private final DataQualityIssueRepository repository;

    public List<DataQualityIssue> openIssues() {
        return repository.findTop100ByStatusOrderByDetectedAtDesc(DataQualityIssueStatus.OPEN);
    }
}
