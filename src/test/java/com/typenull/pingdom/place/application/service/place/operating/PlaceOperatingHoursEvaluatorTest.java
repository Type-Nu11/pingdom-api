package com.typenull.pingdom.place.application.service.place.operating;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingException;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingTimeRange;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingHour;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceOperatingHoursEvaluatorTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /**
     * 서울 화요일 정오가 10~20시 정기 영업 구간 안이면 영업 중으로 판단하는지 확인합니다.
     */
    @Test
    void opensInsideRegularHours() {
        PlaceOperatingHoursEvaluator evaluator = evaluatorAt(LocalDateTime.of(2026, 7, 21, 12, 0));
        MapPlace place = place();
        place.replaceOperatingSchedule(Set.of(
                PlaceRegularOperatingHour.of(DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(20, 0))
        ), List.of());

        PlaceCurrentOperatingState result = evaluator.evaluate(place);

        assertThat(result.currentlyOperating())
                .as("화요일 10:00-20:00 정기 영업시간 안이면 현재 운영 중이어야 한다")
                .isTrue();
    }

    /**
     * 정기 영업 종료 이후인 21시는 영업 중이 아닌지 확인합니다.
     */
    @Test
    void closesOutsideRegularHours() {
        PlaceOperatingHoursEvaluator evaluator = evaluatorAt(LocalDateTime.of(2026, 7, 21, 21, 0));
        MapPlace place = place();
        place.replaceOperatingSchedule(Set.of(
                PlaceRegularOperatingHour.of(DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(20, 0))
        ), List.of());

        PlaceCurrentOperatingState result = evaluator.evaluate(place);

        assertThat(result.currentlyOperating())
                .as("정기 영업 종료 시각 이후면 운영 중이 아니어야 한다")
                .isFalse();
    }

    /**
     * 정기 영업 구간 안이어도 장소가 임시 휴업이면 닫힘으로 판단하는지 확인합니다.
     */
    @Test
    void prioritizesManualClosure() {
        PlaceOperatingHoursEvaluator evaluator = evaluatorAt(LocalDateTime.of(2026, 7, 21, 12, 0));
        MapPlace place = place();
        place.updateOperatingStatus(PlaceOperatingStatus.TEMPORARILY_CLOSED, LocalDateTime.of(2026, 7, 21, 9, 0));
        place.replaceOperatingSchedule(Set.of(
                PlaceRegularOperatingHour.of(DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(20, 0))
        ), List.of());

        PlaceCurrentOperatingState result = evaluator.evaluate(place);

        assertThat(result.currentlyOperating())
                .as("관리자가 임시 휴업으로 둔 장소는 정기 영업시간 안이어도 닫힘이어야 한다")
                .isFalse();
    }

    /**
     * 해당 날짜의 예외 휴무가 정기 영업시간보다 우선하는지 확인합니다.
     */
    @Test
    void prioritizesClosedException() {
        LocalDate date = LocalDate.of(2026, 7, 21);
        PlaceOperatingHoursEvaluator evaluator = evaluatorAt(date.atTime(12, 0));
        MapPlace place = place();
        place.replaceOperatingSchedule(
                Set.of(PlaceRegularOperatingHour.of(DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(20, 0))),
                List.of(PlaceOperatingException.closed(place, date))
        );

        PlaceCurrentOperatingState result = evaluator.evaluate(place);

        assertThat(result.currentlyOperating())
                .as("예외 휴무일이면 정기 영업시간보다 예외 설정이 우선해야 한다")
                .isFalse();
    }

    /**
     * 정기 영업 종료 뒤에도 날짜별 예외 영업 구간 안이면 영업 중인지 확인합니다.
     */
    @Test
    void usesCustomExceptionHours() {
        LocalDate date = LocalDate.of(2026, 7, 21);
        PlaceOperatingHoursEvaluator evaluator = evaluatorAt(date.atTime(22, 0));
        MapPlace place = place();
        place.replaceOperatingSchedule(
                Set.of(PlaceRegularOperatingHour.of(DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(20, 0))),
                List.of(PlaceOperatingException.customHours(
                        place,
                        date,
                        Set.of(PlaceOperatingTimeRange.of(LocalTime.of(21, 0), LocalTime.of(23, 0)))
                ))
        );

        PlaceCurrentOperatingState result = evaluator.evaluate(place);

        assertThat(result.currentlyOperating())
                .as("예외 영업시간이 있으면 해당 날짜에는 예외 시간이 기준이어야 한다")
                .isTrue();
    }

    /**
     * 전날 22시부터 다음 날 02시까지의 영업이 다음 날 01시에도 유지되는지 확인합니다.
     */
    @Test
    void continuesOvernightRegularHours() {
        PlaceOperatingHoursEvaluator evaluator = evaluatorAt(LocalDateTime.of(2026, 7, 22, 1, 0));
        MapPlace place = place();
        place.replaceOperatingSchedule(Set.of(
                PlaceRegularOperatingHour.of(DayOfWeek.TUESDAY, LocalTime.of(22, 0), LocalTime.of(2, 0))
        ), List.of());

        PlaceCurrentOperatingState result = evaluator.evaluate(place);

        assertThat(result.currentlyOperating())
                .as("전날 22:00-익일 02:00 영업은 자정 이후에도 운영 중으로 판단해야 한다")
                .isTrue();
    }

    /**
     * 주어진 서울 지역 시각에 고정된 평가기를 만듭니다.
     */
    private PlaceOperatingHoursEvaluator evaluatorAt(LocalDateTime now) {
        return new PlaceOperatingHoursEvaluator(Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL));
    }

    /**
     * 영업 일정을 사례별로 설정할 기본 장소를 만듭니다.
     */
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
