package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingStatusUpdateRequest;
import com.typenull.pingdom.place.application.service.place.operating.PlaceCurrentOperatingState;
import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingHoursEvaluator;
import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingScheduleInput;
import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingScheduleInput.ExceptionDay;
import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingScheduleInput.RegularHour;
import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingScheduleInput.TimeRange;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingHour;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantOwnerPlaceOperatingServiceTest {

    private static final Long USER_ID = 20L;
    private static final Long PLACE_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);
    private static final LocalTime OPENS = LocalTime.of(9, 0);
    private static final LocalTime CLOSES = LocalTime.of(18, 0);
    @Mock private MapPlaceRepository places;
    @Mock private MerchantPlaceCapabilityPolicy capabilities;
    @Mock private PlaceOperatingHoursEvaluator evaluator;
    private Clock clock;
    private MerchantOwnerPlaceOperatingService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new MerchantOwnerPlaceOperatingService(places, capabilities, evaluator, clock);
    }

    @Test
    void replacesScheduleAndKeepsSortedOwnerSubmittedResponse() {
        MapPlace place = place();
        when(places.findByIdForUpdate(PLACE_ID)).thenReturn(Optional.of(place));
        LocalDate today = NOW.toLocalDate();
        var input = new PlaceOperatingScheduleInput(
                Set.of(new RegularHour(DayOfWeek.TUESDAY, OPENS, CLOSES),
                        new RegularHour(DayOfWeek.MONDAY, OPENS, CLOSES)),
                Set.of(new ExceptionDay(today.plusDays(1), true, null),
                        new ExceptionDay(today, false, Set.of(new TimeRange(OPENS, CLOSES))))
        );

        var response = service.updateOperatingSchedule(USER_ID, PLACE_ID, input);

        assertThat(response.regularHours()).extracting(hour -> hour.dayOfWeek())
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
        assertThat(response.operatingExceptions()).extracting(day -> day.date())
                .containsExactly(today, today.plusDays(1));
        assertThat(place.getPrimaryInformationSource()).isEqualTo(PlaceInformationSourceType.MERCHANT_OWNER);
        assertThat(place.getInformationVerificationStatus()).isEqualTo(PlaceInformationVerificationStatus.OWNER_SUBMITTED);
        assertThat(place.getInformationEvidenceUpdatedAt()).isEqualTo(NOW);
        verify(capabilities).require(USER_ID, PLACE_ID, MerchantPlaceCapability.SCHEDULE_MANAGE);
    }

    @Test
    void omittedCollectionsClearExistingSchedule() {
        MapPlace place = place();
        place.replaceOperatingSchedule(Set.of(PlaceRegularOperatingHour.of(DayOfWeek.MONDAY, OPENS, CLOSES)), List.of());
        when(places.findByIdForUpdate(PLACE_ID)).thenReturn(Optional.of(place));

        var response = service.updateOperatingSchedule(USER_ID, PLACE_ID, new PlaceOperatingScheduleInput(null, null));

        assertThat(response.regularHours()).isEmpty();
        assertThat(place.currentRegularOperatingHours()).isEmpty();
        assertThat(response.operatingExceptions()).isEmpty();
    }

    @Test
    void rejectsDuplicateDatesBeforeReplacingSchedule() {
        MapPlace place = place();
        PlaceRegularOperatingHour existing = PlaceRegularOperatingHour.of(DayOfWeek.MONDAY, OPENS, CLOSES);
        place.replaceOperatingSchedule(Set.of(existing), List.of());
        when(places.findByIdForUpdate(PLACE_ID)).thenReturn(Optional.of(place));
        LocalDate date = NOW.toLocalDate();
        var input = new PlaceOperatingScheduleInput(Set.of(), Set.of(
                new ExceptionDay(date, true, null),
                new ExceptionDay(date, false, Set.of(new TimeRange(OPENS, CLOSES)))
        ));

        assertThatThrownBy(() -> service.updateOperatingSchedule(USER_ID, PLACE_ID, input))
                .isInstanceOfSatisfying(MapException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(MapErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST));
        assertThat(place.currentRegularOperatingHours()).containsExactly(existing);
    }

    @Test
    void rejectsEqualOpeningAndClosingTimes() {
        MapPlace place = place();
        when(places.findByIdForUpdate(PLACE_ID)).thenReturn(Optional.of(place));
        var input = new PlaceOperatingScheduleInput(Set.of(new RegularHour(DayOfWeek.MONDAY, OPENS, OPENS)), Set.of());

        assertThatThrownBy(() -> service.updateOperatingSchedule(USER_ID, PLACE_ID, input))
                .isInstanceOfSatisfying(MapException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(MapErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST));
        assertThat(place.currentRegularOperatingHours()).isEmpty();
    }

    @Test
    void checksCapabilityBeforePlaceLookupOrValidation() {
        doThrow(new IllegalStateException("denied")).when(capabilities)
                .require(USER_ID, PLACE_ID, MerchantPlaceCapability.SCHEDULE_MANAGE);

        assertThatThrownBy(() -> service.updateOperatingSchedule(USER_ID, PLACE_ID, null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(places, evaluator);
    }

    @Test
    void evaluatesChangedOperatingStatusAtTheSameInstant() {
        MapPlace place = place();
        when(places.findByIdForUpdate(PLACE_ID)).thenReturn(Optional.of(place));
        when(evaluator.evaluate(place, clock.instant())).thenReturn(new PlaceCurrentOperatingState(false, NOW));

        var response = service.updateOperatingStatus(USER_ID, PLACE_ID,
                new MerchantOwnerOperatingStatusUpdateRequest(PlaceOperatingStatus.TEMPORARILY_CLOSED));

        assertThat(response.operatingStatus()).isEqualTo(PlaceOperatingStatus.TEMPORARILY_CLOSED);
        assertThat(response.operatingStatusCheckedAt()).isEqualTo(NOW);
        assertThat(response.currentlyOperating()).isFalse();
        verify(evaluator).evaluate(place, clock.instant());
    }

    private MapPlace place() {
        return MapPlace.builder().id(PLACE_ID).name("장소").address("주소")
                .latitude(35.1).longitude(128.1).userId(USER_ID).registrant("merchant").build();
    }
}
