package com.typenull.pingdom.shared.quality;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DataQualityIssueTest {
    @Test
    void opensIssueWithDeterministicRuleAndSeverity() {
        var issue = DataQualityIssue.open("PLACE", 1L, "PLACE_MISSING_COORDINATE",
                DataQualityIssueSeverity.ERROR, "latitude is null", LocalDateTime.now());
        assertThat(issue.getStatus()).isEqualTo(DataQualityIssueStatus.OPEN);
        assertThat(issue.getRuleCode()).isEqualTo("PLACE_MISSING_COORDINATE");
    }
}
