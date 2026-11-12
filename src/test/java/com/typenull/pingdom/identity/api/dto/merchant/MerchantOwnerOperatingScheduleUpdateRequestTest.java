package com.typenull.pingdom.identity.api.dto.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingScheduleUpdateRequest.OperatingExceptionRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingScheduleUpdateRequest.RegularHourRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingScheduleUpdateRequest.TimeRangeRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingExceptionRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingTimeRangeRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceRegularOperatingHourRequest;
import io.swagger.v3.core.converter.ModelConverters;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MerchantOwnerOperatingScheduleUpdateRequestTest {

    @Test
    void mapsMerchantValuesWithoutChangingNullCollectionMeaning() {
        LocalTime opens = LocalTime.of(9, 0);
        LocalTime closes = LocalTime.of(18, 0);
        LocalDate date = LocalDate.of(2026, 11, 12);
        var request = new MerchantOwnerOperatingScheduleUpdateRequest(
                Set.of(new RegularHourRequest(DayOfWeek.MONDAY, opens, closes)),
                Set.of(new OperatingExceptionRequest(date, false, Set.of(new TimeRangeRequest(opens, closes))))
        );

        var input = request.toInput();

        assertThat(input.regularHours()).singleElement().satisfies(hour -> {
            assertThat(hour.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(hour.opensAt()).isEqualTo(opens);
            assertThat(hour.closesAt()).isEqualTo(closes);
        });
        assertThat(input.exceptions()).singleElement().satisfies(exception -> {
            assertThat(exception.date()).isEqualTo(date);
            assertThat(exception.hours()).singleElement().satisfies(hour -> assertThat(hour.opensAt()).isEqualTo(opens));
        });
        var omitted = new MerchantOwnerOperatingScheduleUpdateRequest(null, null).toInput();
        assertThat(omitted.regularHours()).isNull();
        assertThat(omitted.exceptions()).isNull();
    }

    @Test
    void retainsExistingOpenApiComponentNamesAndFields() {
        ModelConverters converters = ModelConverters.getInstance();
        assertThat(converters.readAll(RegularHourRequest.class))
                .isEqualTo(converters.readAll(AdminMapPlaceRegularOperatingHourRequest.class));
        assertThat(converters.readAll(OperatingExceptionRequest.class))
                .isEqualTo(converters.readAll(AdminMapPlaceOperatingExceptionRequest.class));
        assertThat(converters.readAll(TimeRangeRequest.class))
                .isEqualTo(converters.readAll(AdminMapPlaceOperatingTimeRangeRequest.class));
    }
}
