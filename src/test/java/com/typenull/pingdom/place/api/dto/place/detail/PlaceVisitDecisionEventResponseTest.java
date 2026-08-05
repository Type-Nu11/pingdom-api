package com.typenull.pingdom.place.api.dto.place.detail;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.place.domain.event.PlaceEvent;
import com.typenull.pingdom.place.domain.event.PlaceEventScheduleStatus;
import com.typenull.pingdom.place.domain.event.PlaceEventType;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceVisitDecisionEventResponseTest {

    @Test
    void mapsOnlyPublicVisitDecisionFieldsAndComputesOngoingStatus() {
        LocalDateTime checkedAt = LocalDateTime.of(2026, 8, 5, 10, 0);
        PlaceEvent event = PlaceEvent.create(
                MapPlace.builder()
                        .name("방문 결정 이벤트 장소")
                        .address("경상남도 진주시 이벤트로 1")
                        .latitude(35.1801)
                        .longitude(128.1078)
                        .registrant("placeOwner")
                        .build(),
                "K-컬처 팝업",
                "오늘 진행 중인 팝업입니다.",
                PlaceEventType.POP_UP,
                checkedAt.minusHours(1),
                checkedAt.plusHours(1),
                checkedAt.minusHours(2)
        );

        PlaceVisitDecisionEventResponse response = PlaceVisitDecisionEventResponse.from(event, checkedAt);

        assertThat(response.title()).isEqualTo("K-컬처 팝업");
        assertThat(response.eventType()).isEqualTo(PlaceEventType.POP_UP);
        assertThat(response.scheduleStatus()).isEqualTo(PlaceEventScheduleStatus.ONGOING);
        assertThat(response.startAt()).isEqualTo(checkedAt.minusHours(1));
        assertThat(response.endAt()).isEqualTo(checkedAt.plusHours(1));
    }
}
