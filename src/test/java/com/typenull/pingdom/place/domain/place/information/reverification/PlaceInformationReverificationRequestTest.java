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

    /** 소유자 응답 후 완료하면 COMPLETED 상태와 응답·완료 시각이 기록되는지 확인. 관리자 권한은 도메인 테스트의 검증 범위에서 제외. */
    @Test
    void completesRespondedReverification() {
        PlaceInformationReverificationRequest request = request();

        request.respond(20L, "영업시간과 주소를 재확인했습니다.", evidence(), NOW.plusHours(1));
        request.complete(NOW.plusHours(2));

        assertThat(request.getStatus()).isEqualTo(PlaceInformationReverificationStatus.COMPLETED);
        assertThat(request.getRespondedAt()).isEqualTo(NOW.plusHours(1));
        assertThat(request.getCompletedAt()).isEqualTo(NOW.plusHours(2));
    }

    /** 소유자 응답 전 재확인 요청을 바로 완료할 수 없는지 확인. */
    @Test
    void rejectsInvalidCompletion() {
        PlaceInformationReverificationRequest request = request();
        assertThatThrownBy(() -> request.complete(NOW.plusHours(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 알림 횟수·시각을 기록하고 응답 기한 후에는 예외와 함께 REQUESTED 상태를 유지하는지 확인. */
    @Test
    void tracksReminderAndRejectsLateResponse() {
        PlaceInformationReverificationRequest request = request();
        request.remind(NOW.plusHours(1));

        assertThat(request.getReminderCount()).isEqualTo(1);
        assertThat(request.getLastRemindedAt()).isEqualTo(NOW.plusHours(1));
        assertThatThrownBy(() -> request.respond(20L, "늦은 응답", evidence(), NOW.plusDays(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(request.getStatus()).isEqualTo(PlaceInformationReverificationStatus.REQUESTED);
    }

    /** 응답 기한이 하루 뒤인 소유자 20번의 재확인 요청을 생성. */
    private PlaceInformationReverificationRequest request() {
        return PlaceInformationReverificationRequest.create(place(), 20L, "정보 최신성 확인", 7L,
                NOW.plusDays(1), NOW);
    }

    /** 재확인 요청과 근거가 공유할 10번 장소 값을 생성. */
    private MapPlace place() {
        return MapPlace.builder().id(10L).name("테스트 장소").address("서울시 테스트로 1")
                .latitude(37.5d).longitude(127.0d).registrant("admin").build();
    }

    /** 소유자가 제출한 사업자 근거를 OWNER_SUBMITTED로 준비해 응답 완료 조건에 사용. */
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
