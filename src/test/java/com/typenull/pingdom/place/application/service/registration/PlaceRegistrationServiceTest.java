package com.typenull.pingdom.place.application.service.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMemberRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.application.service.localhot.PlaceAdministrativeRegionService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationCategory;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceRegistrationServiceTest {

    @Mock private PlaceRegistrationApplicationRepository applicationRepository;
    @Mock private MapPlaceRepository placeRepository;
    @Mock private PlaceAdministrativeRegionService placeAdministrativeRegionService;
    @Mock private PlaceRecommendationSnapshotService snapshotService;
    @Mock private MerchantOwnerProfileRepository profileRepository;
    @Mock private MerchantOwnerPlaceRepository ownerPlaceRepository;
    @Mock private MerchantPlaceMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserAccessStatusService userAccessStatusService;
    @Mock private PlaceRegistrationMediaPromotionService mediaPromotionService;

    @Test
    void createApprovedPlaceDoesNotReapproveActiveMerchantOwnerProfile() {
        PlaceRegistrationApplication application = mock(PlaceRegistrationApplication.class);
        MapPlace place = mock(MapPlace.class);
        MerchantOwnerProfile profile = MerchantOwnerProfile.builder()
                .userId(10L)
                .businessName("핑덤")
                .displayName("핑덤")
                .contactEmail("owner@pingdom.test")
                .contactPhone("+821012345678")
                .status(MerchantOwnerStatus.ACTIVE)
                .build();
        User user = mock(User.class);
        when(user.getUsername()).thenReturn("owner");

        when(applicationRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(application));
        when(application.getApplicantUserId()).thenReturn(10L);
        when(application.getApplicationType()).thenReturn(MerchantPlaceApplicationType.NEW_PLACE);
        when(application.getStatus()).thenReturn(PlaceRegistrationStatus.APPROVED);
        when(application.getPlaceName()).thenReturn("새 장소");
        when(application.getCategory()).thenReturn(PlaceRegistrationCategory.CAFE);
        when(application.getRoadAddress()).thenReturn("서울시");
        when(application.getJibunAddress()).thenReturn("서울시");
        when(application.getPostalCode()).thenReturn("00000");
        when(application.getDescription()).thenReturn("설명");
        when(application.getOperatingScheduleJson()).thenReturn("[]");
        when(application.getLatitude()).thenReturn(37.5);
        when(application.getLongitude()).thenReturn(127.0);
        when(profileRepository.findByUserIdForUpdate(10L)).thenReturn(Optional.of(profile));
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(user));
        when(placeRepository.existsByNameAndAddressAndLatitudeAndLongitude(anyString(), anyString(), anyDouble(), anyDouble()))
                .thenReturn(false);
        when(placeRepository.save(any(MapPlace.class))).thenReturn(place);
        when(place.getId()).thenReturn(30L);

        PlaceRegistrationService service = new PlaceRegistrationService(
                applicationRepository,
                placeRepository,
                placeAdministrativeRegionService,
                snapshotService,
                profileRepository,
                ownerPlaceRepository,
                memberRepository,
                userRepository,
                userAccessStatusService,
                mediaPromotionService,
                Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC),
                new ObjectMapper()
        );

        Long placeId = service.createApprovedPlaceForUnifiedApplication(10L, 12L);

        assertThat(placeId).isEqualTo(30L);
        assertThat(profile.getStatus()).isEqualTo(MerchantOwnerStatus.ACTIVE);
        verify(user).activateMerchantOwnerRole();
        verify(placeRepository).save(any(MapPlace.class));
    }
}
