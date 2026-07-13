package com.typenull.pingdom.identity.application.query;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.travel.CurrentActivityIntent;
import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
import com.typenull.pingdom.identity.domain.travel.TravelScheduleState;
import com.typenull.pingdom.identity.domain.travel.UserCurrentActivityIntent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record UserDataExportResult(
        ExportUser user,
        List<ExportBookmark> bookmarks,
        List<Long> likedMapImageIds,
        List<ExportTravelSchedule> travelSchedules,
        ExportCurrentActivityIntent currentActivityIntent
) {

    public static UserDataExportResult of(
            User user,
            List<ExportBookmark> bookmarks,
            List<Long> likedMapImageIds,
            List<TravelSchedule> travelSchedules,
            UserCurrentActivityIntent currentActivityIntent
    ) {
        return new UserDataExportResult(
                new ExportUser(user.getId(), user.getUsername(), user.getProfileImageUrl()),
                bookmarks,
                likedMapImageIds,
                travelSchedules.stream()
                        .map(schedule -> new ExportTravelSchedule(
                                schedule.getId(),
                                schedule.getStartDate(),
                                schedule.getEndDate(),
                                schedule.getState()
                        ))
                        .toList(),
                currentActivityIntent == null
                        ? null
                        : new ExportCurrentActivityIntent(
                                currentActivityIntent.getIntent(),
                                currentActivityIntent.getExpiresAt()
                        )
        );
    }

    public record ExportUser(
            Long id,
            String username,
            String profileImageUrl
    ) {
    }

    public record ExportBookmark(
            Long id,
            Long placeId
    ) {
    }

    public record ExportTravelSchedule(
            Long id,
            LocalDate startDate,
            LocalDate endDate,
            TravelScheduleState state
    ) {
    }

    public record ExportCurrentActivityIntent(
            CurrentActivityIntent intent,
            LocalDateTime expiresAt
    ) {
    }
}
