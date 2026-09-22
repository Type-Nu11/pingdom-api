package com.typenull.pingdom.place.domain.place.operating.notice;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceOperatingNoticeTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 21, 12, 0);

    /** 시작 시각이 지난 유효 공지는 생성 즉시 ACTIVE이며 현재 시각에 노출 가능한지 확인한다. */
    @Test
    void activatesAlreadyStartedNotice() {
        PlaceOperatingNotice notice = createNotice(NOW.minusMinutes(10), NOW.plusHours(2));

        assertThat(notice.getStatus())
                .as("시작 시각이 지난 공지는 즉시 ACTIVE 상태여야 한다")
                .isEqualTo(PlaceOperatingNoticeStatus.ACTIVE);
        assertThat(notice.isVisibleAt(NOW))
                .as("ACTIVE 공지는 기간 안에서 관광객에게 노출 가능해야 한다")
                .isTrue();
    }

    /** 미래 공지는 SCHEDULED로 시작하고 시작 전 활성화를 거부하되 정확한 시작 시각에는 ACTIVE로 바뀌는지 확인한다. */
    @Test
    void activatesNoticeAtScheduledStart() {
        PlaceOperatingNotice notice = createNotice(NOW.plusMinutes(30), NOW.plusHours(2));

        assertThat(notice.getStatus()).isEqualTo(PlaceOperatingNoticeStatus.SCHEDULED);
        assertThatThrownBy(() -> notice.activate(NOW.plusMinutes(10)))
                .as("예약 공지는 startsAt 이전에 ACTIVE로 전환되면 안 된다")
                .isInstanceOf(IllegalStateException.class);

        notice.activate(NOW.plusMinutes(30));

        assertThat(notice.getStatus()).isEqualTo(PlaceOperatingNoticeStatus.ACTIVE);
    }

    /** 만료 전 전환은 거부하고 정확한 만료 시각에는 EXPIRED·만료 시각을 저장하여 이후 노출되지 않는지 확인한다. */
    @Test
    void expiresOnlyAfterExpirationTime() {
        PlaceOperatingNotice notice = createNotice(NOW.minusMinutes(10), NOW.plusMinutes(1));

        assertThatThrownBy(() -> notice.expire(NOW))
                .as("자동 만료 처리는 expiresAt 이전에 실행되면 원인을 식별할 수 있어야 한다")
                .isInstanceOf(IllegalStateException.class);

        notice.expire(NOW.plusMinutes(1));

        assertThat(notice.getStatus()).isEqualTo(PlaceOperatingNoticeStatus.EXPIRED);
        assertThat(notice.getExpiredAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(notice.isVisibleAt(NOW.plusMinutes(2))).isFalse();
    }

    /** 공백 취소 사유를 거부하고 정상 취소 후 사유·상태를 기록하며 종료 공지 수정을 막는지 확인한다. */
    @Test
    void guardsCanceledNoticeState() {
        PlaceOperatingNotice notice = createNotice(NOW.minusMinutes(10), NOW.plusHours(2));

        assertThatThrownBy(() -> notice.cancel(2L, " ", NOW))
                .as("취소 사유 누락은 명확한 예외로 식별되어야 한다")
                .isInstanceOf(IllegalArgumentException.class);

        notice.cancel(2L, "점주 요청으로 공지 종료", NOW.plusMinutes(5));

        assertThat(notice.getStatus()).isEqualTo(PlaceOperatingNoticeStatus.CANCELED);
        assertThat(notice.getCancelReason()).isEqualTo("점주 요청으로 공지 종료");
        assertThatThrownBy(() -> notice.updateContent(
                PlaceOperatingNoticeSeverity.WARNING,
                "다시 수정",
                2L,
                NOW.plusMinutes(6)
        ))
                .as("종료 상태 공지는 다시 수정될 수 없어야 한다")
                .isInstanceOf(IllegalStateException.class);
    }

    /** 시작과 종료가 같은 기간 및 공백 메시지를 각 오류 메시지로 거부하는지 확인한다. */
    @Test
    void rejectsInvalidNoticeInput() {
        assertThatThrownBy(() -> createNotice(NOW.plusHours(1), NOW.plusHours(1)))
                .as("공지 시작/종료 시간이 같으면 DB 제약 전에 도메인에서 거절해야 한다")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startsAt must be before expiresAt");

        assertThatThrownBy(() -> PlaceOperatingNotice.create(
                place(),
                PlaceOperatingNoticeType.GENERAL,
                PlaceOperatingNoticeSeverity.INFO,
                " ",
                NOW,
                NOW.plusHours(1),
                1L,
                NOW
        ))
                .as("빈 공지 메시지는 도메인에서 거절해야 한다")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("message must not be blank");
    }

    /** 전달된 기간과 고정 현재 시각으로 임시 휴업 공지를 생성한다. */
    private PlaceOperatingNotice createNotice(LocalDateTime startsAt, LocalDateTime expiresAt) {
        return PlaceOperatingNotice.create(
                place(),
                PlaceOperatingNoticeType.TEMPORARY_CLOSURE,
                PlaceOperatingNoticeSeverity.WARNING,
                "오늘 내부 사정으로 임시 휴업합니다.",
                startsAt,
                expiresAt,
                1L,
                NOW
        );
    }

    /** 공지와 연결할 소유자·위치 정보가 있는 테스트 상점을 만든다. */
    private MapPlace place() {
        return MapPlace.builder()
                .id(10L)
                .name("테스트 상점")
                .address("서울시 성동구 테스트로 1")
                .latitude(37.5445d)
                .longitude(127.0557d)
                .userId(1L)
                .registrant("merchant")
                .build();
    }
}
