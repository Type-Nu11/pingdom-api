package com.typenull.pingdom.shared.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataQualityMonitoringServiceTest {
    @Test
    void returnsOpenIssuesForMonitoring() {
        var repository = mock(DataQualityIssueRepository.class);
        var issue = DataQualityIssue.open("PLACE", 1L, "MISSING_COORDINATE", DataQualityIssueSeverity.ERROR,
                "missing", java.time.LocalDateTime.now());
        when(repository.findTop100ByStatusOrderByDetectedAtDesc(DataQualityIssueStatus.OPEN))
                .thenReturn(List.of(issue));
        assertThat(new DataQualityMonitoringService(repository).openIssues()).containsExactly(issue);
    }
}
