package com.typenull.pingdom.identity.application.query;

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
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.repository.TravelScheduleRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserCurrentActivityIntentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.infrastructure.crypto.MerchantVerificationCipher;
import com.typenull.pingdom.identity.domain.travel.CurrentActivityIntent;
import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
import com.typenull.pingdom.identity.domain.travel.UserCurrentActivityIntent;
import com.typenull.pingdom.place.domain.place.core.MapBookmark;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.offer.domain.CouponStatus;
import com.typenull.pingdom.offer.domain.OfferStatus;
import com.typenull.pingdom.offer.domain.TouristCoupon;
import com.typenull.pingdom.offer.domain.TouristOffer;
import com.typenull.pingdom.offer.infrastructure.TouristCouponRepository;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import com.typenull.pingdom.privacy.application.PrivacyProcessingOutboxPublisher;
import com.typenull.pingdom.privacy.event.PrivacyProcessingEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

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
    private MerchantOwnerProfileRepository merchantOwnerProfileRepository;

    @Mock
    private MerchantOwnerPlaceRepository merchantOwnerPlaceRepository;

    @Mock
    private MerchantVerificationRepository merchantVerificationRepository;

    @Mock
    private TouristOfferRepository touristOfferRepository;

    @Mock
    private TouristCouponRepository touristCouponRepository;

    @Mock
    private MerchantVerificationCipher merchantVerificationCipher;

    @Mock
    private PrivacyProcessingOutboxPublisher privacyProcessingOutboxPublisher;

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
        MerchantVerification merchantVerification = MerchantVerification.pending(
                userId,
                "김핑덤",
                "핑덤 카페",
                "encrypted-number",
                LocalDateTime.of(2026, 7, 15, 12, 0)
        );
        merchantVerification.review(
                99L,
                true,
                true,
                "확인 완료",
                LocalDateTime.of(2026, 7, 15, 13, 0)
        );
        TouristOffer touristOffer = TouristOffer.draft(
                userId,
                123L,
                "관광객 Offer",
                "관광객 전용 설명",
                "음료 1잔 무료",
                LocalDateTime.of(2026, 8, 1, 9, 0),
                LocalDateTime.of(2026, 8, 10, 23, 59),
                100,
                3,
                LocalDateTime.of(2026, 7, 20, 10, 0)
        );
        ReflectionTestUtils.setField(touristOffer, "id", 50L);
        touristOffer.publish(LocalDateTime.of(2026, 7, 20, 11, 0));
        TouristCoupon touristCoupon = TouristCoupon.issue(
                50L,
                userId,
                "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                LocalDateTime.of(2026, 7, 31, 10, 0),
                LocalDateTime.of(2026, 8, 1, 9, 0)
        );
        ReflectionTestUtils.setField(touristCoupon, "id", 60L);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mapBookmarkRepository.findByUserIdOrderByIdAsc(userId)).thenReturn(List.of(bookmark));
        when(mapImageLikeRepository.findRecentMapImageIdsByUserId(eq(userId), any(Pageable.class)))
                .thenReturn(List.of(981L, 812L, 700L));
        when(travelScheduleRepository.findAllByUser_IdOrderByStartDateAscIdAsc(userId))
                .thenReturn(List.of(travelSchedule));
        when(currentActivityIntentRepository.findByUser_Id(userId)).thenReturn(Optional.of(activityIntent));
        when(merchantVerificationRepository.findById(userId)).thenReturn(Optional.of(merchantVerification));
        when(merchantVerificationCipher.decrypt("encrypted-number")).thenReturn("1234567890");
        when(touristOfferRepository.findAllByMerchantOwnerUserIdOrderByCreatedAtDescIdDesc(userId))
                .thenReturn(List.of(touristOffer));
        when(touristCouponRepository.findAllByUserIdOrderByIssuedAtDescIdDesc(userId))
                .thenReturn(List.of(touristCoupon));

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
        assertThat(result.merchantVerification().businessRegistrationNumber()).isEqualTo("1234567890");
        assertThat(result.merchantVerification().identityStatus())
                .isEqualTo(MerchantVerificationStatus.APPROVED);
        assertThat(result.touristOffers())
                .extracting(
                        UserDataExportResult.ExportTouristOffer::id,
                        UserDataExportResult.ExportTouristOffer::status
                )
                .containsExactly(tuple(50L, OfferStatus.PUBLISHED));
        assertThat(result.touristCoupons())
                .extracting(
                        UserDataExportResult.ExportTouristCoupon::id,
                        UserDataExportResult.ExportTouristCoupon::status
                )
                .containsExactly(tuple(60L, CouponStatus.EXPIRED));

        ArgumentCaptor<PrivacyProcessingEvent> eventCaptor = ArgumentCaptor.forClass(PrivacyProcessingEvent.class);
        verify(privacyProcessingOutboxPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .extracting(PrivacyProcessingEvent::subjectUserId, PrivacyProcessingEvent::actorUserId, PrivacyProcessingEvent::action)
                .containsExactly(userId, userId, com.typenull.pingdom.privacy.domain.PrivacyProcessingAction.EXPORT_REQUESTED);
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
