package com.typenull.pingdom.shared.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class DataQualityMonitoringServiceTest {
    @Test
    void returnsOpenIssuesForMonitoring() {
        var repository = mock(DataQualityIssueRepository.class);
        var issue = DataQualityIssue.open("PLACE", 1L, "MISSING_COORDINATE", DataQualityIssueSeverity.ERROR,
                "missing", java.time.LocalDateTime.now());
        when(repository.findAllByStatus(org.mockito.Mockito.eq(DataQualityIssueStatus.OPEN), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(java.util.List.of(issue), PageRequest.of(0, 20), 1));
        assertThat(new DataQualityMonitoringService(repository).openIssues(1, 20).getContent()).containsExactly(issue);
    }
}
