package com.typenull.pingdom.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.identity.application.query.UserDataExportResult;
import com.typenull.pingdom.identity.application.query.UserDataExportService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.TravelScheduleRepository;
import com.typenull.pingdom.identity.domain.repository.UserCurrentActivityIntentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.travel.CurrentActivityIntent;
import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
import com.typenull.pingdom.identity.domain.travel.UserCurrentActivityIntent;
import com.typenull.pingdom.place.domain.place.core.MapBookmark;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.event.PrivacyProcessingEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class UserDataExportServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MapBookmarkRepository mapBookmarkRepository;

    @Mock
    private MapImageLikeRepository mapImageLikeRepository;

    @Mock
    private TravelScheduleRepository travelScheduleRepository;

    @Mock
    private UserCurrentActivityIntentRepository currentActivityIntentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Clock clock;

    @InjectMocks
    private UserDataExportService userDataExportService;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-08-01T10:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void 내_데이터를_정해진_형태로_내보낸다() {
        Long userId = 1L;
        User user = User.builder()
                .id(userId)
                .username("pingdom_user")
                .profileImageUrl("https://cdn.pingdom.com/profiles/user1.png")
                .build();
        MapBookmark bookmark = MapBookmark.builder()
                .id(10L)
                .userId(userId)
                .placeId(123L)
                .build();
        TravelSchedule travelSchedule = TravelSchedule.create(
                user,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 4)
        );
        UserCurrentActivityIntent activityIntent = UserCurrentActivityIntent.create(
                user,
                CurrentActivityIntent.CAFE,
                LocalDate.of(2026, 8, 1).atTime(12, 0)
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mapBookmarkRepository.findByUserIdOrderByIdAsc(userId)).thenReturn(List.of(bookmark));
        when(mapImageLikeRepository.findRecentMapImageIdsByUserId(eq(userId), any(Pageable.class)))
                .thenReturn(List.of(981L, 812L, 700L));
        when(travelScheduleRepository.findAllByUser_IdOrderByStartDateAscIdAsc(userId))
                .thenReturn(List.of(travelSchedule));
        when(currentActivityIntentRepository.findByUser_Id(userId)).thenReturn(Optional.of(activityIntent));

        UserDataExportResult result = userDataExportService.exportMyData(userId);

        assertThat(result.user().id()).isEqualTo(userId);
        assertThat(result.user().username()).isEqualTo("pingdom_user");
        assertThat(result.user().profileImageUrl()).isEqualTo("https://cdn.pingdom.com/profiles/user1.png");
        assertThat(result.bookmarks())
                .extracting(UserDataExportResult.ExportBookmark::id, UserDataExportResult.ExportBookmark::placeId)
                .containsExactly(tuple(10L, 123L));
        assertThat(result.likedMapImageIds()).containsExactly(981L, 812L, 700L);
        assertThat(result.travelSchedules()).hasSize(1);
        assertThat(result.travelSchedules().getFirst().startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(result.currentActivityIntent())
                .extracting(UserDataExportResult.ExportCurrentActivityIntent::intent)
                .isEqualTo(CurrentActivityIntent.CAFE);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .asInstanceOf(InstanceOfAssertFactories.type(PrivacyProcessingEvent.class))
                .extracting(PrivacyProcessingEvent::subjectUserId, PrivacyProcessingEvent::actorUserId, PrivacyProcessingEvent::action)
                .containsExactly(userId, userId, PrivacyProcessingAction.EXPORT_REQUESTED);
    }

    @Test
    void 좋아요는_최대_50개만_조회한다() {
        Long userId = 1L;
        User user = User.builder()
                .id(userId)
                .username("pingdom_user")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mapBookmarkRepository.findByUserIdOrderByIdAsc(userId)).thenReturn(List.of());
        when(mapImageLikeRepository.findRecentMapImageIdsByUserId(eq(userId), any(Pageable.class)))
                .thenReturn(List.of());
        when(travelScheduleRepository.findAllByUser_IdOrderByStartDateAscIdAsc(userId)).thenReturn(List.of());
        when(currentActivityIntentRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        userDataExportService.exportMyData(userId);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(mapImageLikeRepository).findRecentMapImageIdsByUserId(eq(userId), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void 만료된_현재_행동_의도는_내보내기에서_제외한다() {
        Long userId = 1L;
        User user = User.builder().id(userId).username("pingdom_user").build();
        UserCurrentActivityIntent expiredIntent = UserCurrentActivityIntent.create(
                user,
                CurrentActivityIntent.CAFE,
                LocalDateTime.of(2026, 8, 1, 10, 0)
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mapBookmarkRepository.findByUserIdOrderByIdAsc(userId)).thenReturn(List.of());
        when(mapImageLikeRepository.findRecentMapImageIdsByUserId(eq(userId), any(Pageable.class)))
                .thenReturn(List.of());
        when(travelScheduleRepository.findAllByUser_IdOrderByStartDateAscIdAsc(userId)).thenReturn(List.of());
        when(currentActivityIntentRepository.findByUser_Id(userId)).thenReturn(Optional.of(expiredIntent));

        UserDataExportResult result = userDataExportService.exportMyData(userId);

        assertThat(result.currentActivityIntent()).isNull();
    }
}
