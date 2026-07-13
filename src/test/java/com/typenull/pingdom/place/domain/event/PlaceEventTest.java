package com.typenull.pingdom.place.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.typenull.pingdom.place.domain.place.MapPlace;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceEventTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 13, 9, 0);
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 8, 31, 20, 0);

    @Test
    void createsDraftAndCalculatesScheduleStatus() {
        PlaceEvent event = createEvent();

        assertThat(event.getPublicationStatus()).isEqualTo(PlaceEventPublicationStatus.DRAFT);
        assertThat(event.scheduleStatusAt(START_AT.minusMinutes(1))).isEqualTo(PlaceEventScheduleStatus.UPCOMING);
        assertThat(event.scheduleStatusAt(START_AT)).isEqualTo(PlaceEventScheduleStatus.ONGOING);
        assertThat(event.scheduleStatusAt(END_AT)).isEqualTo(PlaceEventScheduleStatus.ENDED);
    }

    @Test
    void rejectsNonIncreasingPeriod() {
        assertThatIllegalArgumentException().isThrownBy(() -> PlaceEvent.create(
                place(),
                "기간 오류 이벤트",
                null,
                PlaceEventType.EXHIBITION,
                START_AT,
                START_AT,
                CREATED_AT
        ));
    }

    @Test
    void publishesOnlyDraftThatHasNotEnded() {
        PlaceEvent event = createEvent();

        event.publish(CREATED_AT);

        assertThat(event.getPublicationStatus()).isEqualTo(PlaceEventPublicationStatus.PUBLISHED);
        assertThatIllegalStateException().isThrownBy(() -> event.publish(CREATED_AT));
        assertThatIllegalStateException().isThrownBy(() -> event.update(
                place(),
                "수정 시도",
                null,
                PlaceEventType.EXHIBITION,
                START_AT,
                END_AT,
                CREATED_AT
        ));
    }

    @Test
    void doesNotPublishEndedEventAndCancelsOnce() {
        PlaceEvent event = createEvent();

        assertThatIllegalStateException().isThrownBy(() -> event.publish(END_AT));

        event.cancel(CREATED_AT);

        assertThat(event.getPublicationStatus()).isEqualTo(PlaceEventPublicationStatus.CANCELLED);
        assertThatIllegalStateException().isThrownBy(() -> event.cancel(CREATED_AT));
    }

    private PlaceEvent createEvent() {
        return PlaceEvent.create(
                place(),
                "진주 여름 빛 축제",
                "남강 야간 전시",
                PlaceEventType.EXHIBITION,
                START_AT,
                END_AT,
                CREATED_AT
        );
    }

    private MapPlace place() {
        return MapPlace.builder()
                .id(1L)
                .name("진주성")
                .address("경상남도 진주시 남강로 626")
                .latitude(35.1801)
                .longitude(128.1078)
                .registrant("admin")
                .build();
    }
}
