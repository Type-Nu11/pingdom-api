package com.typenull.pingdom.place.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceEventTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 13, 9, 0);
    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 8, 31, 20, 0);

    /** 이벤트가 DRAFT로 생성되고 시작 직전·시작 시각·종료 시각을 UPCOMING·ONGOING·ENDED로 구분하는지 확인한다. */
    @Test
    void calculatesDraftScheduleStatus() {
        PlaceEvent event = createEvent();

        assertThat(event.getPublicationStatus()).isEqualTo(PlaceEventPublicationStatus.DRAFT);
        assertThat(event.scheduleStatusAt(START_AT.minusMinutes(1))).isEqualTo(PlaceEventScheduleStatus.UPCOMING);
        assertThat(event.scheduleStatusAt(START_AT)).isEqualTo(PlaceEventScheduleStatus.ONGOING);
        assertThat(event.scheduleStatusAt(END_AT)).isEqualTo(PlaceEventScheduleStatus.ENDED);
    }

    /** 시작과 종료가 같은 이벤트 기간을 거부하는지 확인한다. */
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

    /** 초안을 발행한 뒤 중복 발행과 내용 수정을 모두 거부하는지 확인한다. */
    @Test
    void rejectsPublishedEventMutation() {
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

    /** 종료 시각의 발행을 거부하고 초안 취소 후 다시 취소할 수 없는지 확인한다. */
    @Test
    void guardsEventPublicationAndCancellation() {
        PlaceEvent event = createEvent();

        assertThatIllegalStateException().isThrownBy(() -> event.publish(END_AT));

        event.cancel(CREATED_AT);

        assertThat(event.getPublicationStatus()).isEqualTo(PlaceEventPublicationStatus.CANCELLED);
        assertThatIllegalStateException().isThrownBy(() -> event.cancel(CREATED_AT));
    }

    /** 이미 발행된 이벤트도 취소 상태로 전환할 수 있는지 확인한다. */
    @Test
    void cancelsPublishedEvent() {
        PlaceEvent event = createEvent();
        event.publish(CREATED_AT);

        event.cancel(CREATED_AT);

        assertThat(event.getPublicationStatus()).isEqualTo(PlaceEventPublicationStatus.CANCELLED);
    }

    /** 고정 전시 기간과 생성 시각을 가진 초안을 만들어 경계 시각 전이를 비교한다. */
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

    /** 이벤트를 연결할 고정 식별자의 장소를 만든다. 저장소를 호출하지 않는다. */
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
