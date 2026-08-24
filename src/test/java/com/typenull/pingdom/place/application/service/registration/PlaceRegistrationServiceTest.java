package com.typenull.pingdom.place.application.service.registration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMemberRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationCategory;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationOperatingDay;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceRegistrationServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long APPLICATION_ID = 20L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC);

    @Mock private PlaceRegistrationApplicationRepository applicationRepository;
    @Mock private MapPlaceRepository placeRepository;
    @Mock private PlaceRecommendationSnapshotService snapshotService;
    @Mock private MerchantOwnerProfileRepository profileRepository;
    @Mock private MerchantOwnerPlaceRepository ownerPlaceRepository;
    @Mock private MerchantPlaceMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserAccessStatusService userAccessStatusService;
    @Mock private ObjectMapper objectMapper;

    private PlaceRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new PlaceRegistrationService(
                applicationRepository,
                placeRepository,
                snapshotService,
                profileRepository,
                ownerPlaceRepository,
                memberRepository,
                userRepository,
                userAccessStatusService,
                CLOCK,
                objectMapper
        );
    }

    @Test
    void unifiedApprovedPlaceCreationEvictsAccessCacheAfterRoleActivation() throws Exception {
        PlaceRegistrationApplication application = org.mockito.Mockito.mock(PlaceRegistrationApplication.class);
        MerchantOwnerProfile profile = org.mockito.Mockito.mock(MerchantOwnerProfile.class);
        User user = org.mockito.Mockito.mock(User.class);
        MapPlace savedPlace = MapPlace.builder().id(30L).build();

        when(applicationRepository.findByIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(application.getApplicantUserId()).thenReturn(USER_ID);
        when(application.getApplicationType()).thenReturn(MerchantPlaceApplicationType.NEW_PLACE);
        when(application.getStatus()).thenReturn(PlaceRegistrationStatus.APPROVED);
        when(application.getPlaceName()).thenReturn("테스트 장소");
        when(application.getRoadAddress()).thenReturn("서울시 테스트로 1");
        when(application.getJibunAddress()).thenReturn("서울시 테스트동 1");
        when(application.getPostalCode()).thenReturn("01234");
        when(application.getCategory()).thenReturn(PlaceRegistrationCategory.CAFE);
        when(application.getLatitude()).thenReturn(37.5);
        when(application.getLongitude()).thenReturn(127.0);
        when(application.getReviewerUserId()).thenReturn(99L);
        when(application.getOperatingScheduleJson()).thenReturn("[]");
        when(placeRepository.existsByNameAndAddressAndLatitudeAndLongitude("테스트 장소", "서울시 테스트로 1", 37.5, 127.0))
                .thenReturn(false);
        when(profileRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(profile));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(user.getUsername()).thenReturn("merchant");
        when(placeRepository.save(any(MapPlace.class))).thenReturn(savedPlace);
        when(objectMapper.readValue(
                eq("[]"),
                org.mockito.ArgumentMatchers.<TypeReference<List<PlaceRegistrationOperatingDay>>>any()
        )).thenReturn(List.of());

        service.createApprovedPlaceForUnifiedApplication(USER_ID, APPLICATION_ID);

        org.mockito.InOrder roleActivationOrder = org.mockito.Mockito.inOrder(user, userAccessStatusService);
        roleActivationOrder.verify(user).activateMerchantOwnerRole();
        roleActivationOrder.verify(userAccessStatusService).evict(USER_ID);
        verify(snapshotService).initialize(30L);
    }
}
