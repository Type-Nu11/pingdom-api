package com.typenull.pingdom.identity.domain.travel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.identity.domain.User;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TravelScheduleTest {

    @Test
    void calculatesStatusFromDatesAndKeepsCancellationTerminal() {
        TravelSchedule schedule = TravelSchedule.create(
                User.builder().build(),
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12)
        );

        assertThat(schedule.statusAt(LocalDate.of(2026, 8, 9))).isEqualTo(TravelScheduleStatus.UPCOMING);
        assertThat(schedule.statusAt(LocalDate.of(2026, 8, 10))).isEqualTo(TravelScheduleStatus.ONGOING);
        assertThat(schedule.statusAt(LocalDate.of(2026, 8, 13))).isEqualTo(TravelScheduleStatus.ENDED);

        schedule.cancel();

        assertThat(schedule.statusAt(LocalDate.of(2026, 8, 10))).isEqualTo(TravelScheduleStatus.CANCELLED);
        assertThatThrownBy(() -> schedule.updatePeriod(
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 12)
        )).isInstanceOf(IllegalStateException.class);
    }
}
