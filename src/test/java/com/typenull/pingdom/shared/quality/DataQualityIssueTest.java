package com.typenull.pingdom.shared.quality;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DataQualityIssueTest {
    /**
     * 장소 좌표 누락 이슈 생성 시 OPEN 상태와 지정 규칙 코드가 유지되는지 검증. 심각도 값은 직접 검증 대상에서 제외.
     */
    @Test
    void opensIssueWithRuleCode() {
        var issue = DataQualityIssue.open("PLACE", 1L, "PLACE_MISSING_COORDINATE",
                DataQualityIssueSeverity.ERROR, "latitude is null", LocalDateTime.now());
        assertThat(issue.getStatus()).isEqualTo(DataQualityIssueStatus.OPEN);
        assertThat(issue.getRuleCode()).isEqualTo("PLACE_MISSING_COORDINATE");
    }
}
