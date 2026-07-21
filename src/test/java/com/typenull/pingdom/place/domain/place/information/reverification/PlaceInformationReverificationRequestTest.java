package com.typenull.pingdom.place.domain.place.information.reverification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationEvidence;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationEvidenceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceInformationReverificationRequestTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 21, 12, 0);

    @Test
    void ownerRespondsAndAdminCompletesRequest() {
        PlaceInformationReverificationRequest request = request();

        request.respond(20L, "영업시간과 주소를 재확인했습니다.", evidence(), NOW.plusHours(1));
        request.complete(NOW.plusHours(2));

        assertThat(request.getStatus()).isEqualTo(PlaceInformationReverificationStatus.COMPLETED);
        assertThat(request.getRespondedAt()).isEqualTo(NOW.plusHours(1));
        assertThat(request.getCompletedAt()).isEqualTo(NOW.plusHours(2));
    }

    @Test
    void rejectsInvalidCompletion() {
        PlaceInformationReverificationRequest request = request();
        assertThatThrownBy(() -> request.complete(NOW.plusHours(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reminderTracksCountAndExpiredRequestCannotRespond() {
        PlaceInformationReverificationRequest request = request();
        request.remind(NOW.plusHours(1));

        assertThat(request.getReminderCount()).isEqualTo(1);
        assertThat(request.getLastRemindedAt()).isEqualTo(NOW.plusHours(1));
        assertThatThrownBy(() -> request.respond(20L, "늦은 응답", evidence(), NOW.plusDays(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(request.getStatus()).isEqualTo(PlaceInformationReverificationStatus.REQUESTED);
    }

    private PlaceInformationReverificationRequest request() {
        return PlaceInformationReverificationRequest.create(place(), 20L, "정보 최신성 확인", 7L,
                NOW.plusDays(1), NOW);
    }

    private MapPlace place() {
        return MapPlace.builder().id(10L).name("테스트 장소").address("서울시 테스트로 1")
                .latitude(37.5d).longitude(127.0d).registrant("admin").build();
    }

    private PlaceInformationEvidence evidence() {
        PlaceInformationEvidence evidence = PlaceInformationEvidence.submit(
                place(), PlaceInformationSourceType.MERCHANT_OWNER,
                PlaceInformationEvidenceType.BUSINESS_CLAIM, null, null,
                "재확인 응답", 20L, NOW
        );
        evidence.markOwnerSubmitted(NOW);
        return evidence;
    }
}
