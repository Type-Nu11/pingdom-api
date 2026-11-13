package com.typenull.pingdom.moderation.domain.appeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ReportAppealTest {

    private static final LocalDateTime FIRST = LocalDateTime.of(2026, 11, 12, 12, 0);

    @ParameterizedTest
    @EnumSource(value = ReportAppealStatus.class, names = {"APPROVED", "REJECTED"})
    void completedDecisionCannotBeOverwritten(ReportAppealStatus completedStatus) {
        ReportAppeal appeal = ReportAppeal.builder().build();
        if (completedStatus == ReportAppealStatus.APPROVED) {
            appeal.approve(1L, "first", FIRST);
        } else {
            appeal.reject(1L, "first", FIRST);
        }

        assertAlreadyProcessed(() -> appeal.approve(2L, "second", FIRST.plusMinutes(1)));
        assertAlreadyProcessed(() -> appeal.reject(2L, "second", FIRST.plusMinutes(1)));

        assertThat(appeal.getStatus()).isEqualTo(completedStatus);
        assertThat(appeal.getAdminUserId()).isEqualTo(1L);
        assertThat(appeal.getAdminReason()).isEqualTo("first");
        assertThat(appeal.getProcessedAt()).isEqualTo(FIRST);
    }

    private void assertAlreadyProcessed(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AdminException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(AdminErrorCode.APPEAL_ALREADY_PROCESSED));
    }
}
