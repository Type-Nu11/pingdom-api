package com.typenull.pingdom.shared.quality;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 미해결 품질 이슈를 읽기 전용 트랜잭션에서 최신 발견 순으로 조회한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataQualityMonitoringService {
    private final DataQualityIssueRepository repository;

    /** 외부의 1부터 시작하는 페이지를 0 기반으로 바꾸고 크기를 1~100으로 제한한다. 같은 발견 시각은 ID 내림차순으로 정렬한다. */
    public Page<DataQualityIssue> openIssues(int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return repository.findAllByStatus(
                DataQualityIssueStatus.OPEN,
                PageRequest.of(safePage - 1, safeLimit, Sort.by("detectedAt").descending().and(Sort.by("id").descending()))
        );
    }
}
