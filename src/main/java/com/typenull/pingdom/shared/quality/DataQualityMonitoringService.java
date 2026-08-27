package com.typenull.pingdom.shared.quality;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataQualityMonitoringService {
    private final DataQualityIssueRepository repository;

    public Page<DataQualityIssue> openIssues(int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return repository.findAllByStatus(
                DataQualityIssueStatus.OPEN,
                PageRequest.of(safePage - 1, safeLimit, Sort.by("detectedAt").descending().and(Sort.by("id").descending()))
        );
    }
}
