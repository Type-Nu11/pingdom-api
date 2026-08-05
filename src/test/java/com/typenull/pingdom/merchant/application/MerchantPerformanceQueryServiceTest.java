package com.typenull.pingdom.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.availability.application.AvailabilityAccessPolicy;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.reservation.domain.ReservationStatus;
import com.typenull.pingdom.reservation.infrastructure.ReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MerchantPerformanceQueryServiceTest {
    private final MerchantOwnerPlaceRepository ownerPlaceRepository = mock(MerchantOwnerPlaceRepository.class);
    private final PlaceRecommendationExposureRepository exposureRepository =
            mock(PlaceRecommendationExposureRepository.class);
    private final PlaceRecommendationClickRepository clickRepository = mock(PlaceRecommendationClickRepository.class);
    private final MapBookmarkRepository bookmarkRepository = mock(MapBookmarkRepository.class);
    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final AvailabilityAccessPolicy accessPolicy = mock(AvailabilityAccessPolicy.class);
    private MerchantPerformanceQueryService service;

    @BeforeEach
    void setUp() {
        service = new MerchantPerformanceQueryService(
                ownerPlaceRepository,
                exposureRepository,
                clickRepository,
                bookmarkRepository,
                reservationRepository,
                accessPolicy,
                Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void aggregatesOwnedPlaceConversionMetrics() {
        when(ownerPlaceRepository.findAllByMerchantOwnerUserIdOrderByPlaceIdAsc(7L)).thenReturn(List.of(
                MerchantOwnerPlace.builder().placeId(10L).merchantOwnerUserId(7L).build(),
                MerchantOwnerPlace.builder().placeId(20L).merchantOwnerUserId(7L).build()
        ));
        PlaceRecommendationExposureRepository.PlaceExposureCountProjection exposures = mock(
                PlaceRecommendationExposureRepository.PlaceExposureCountProjection.class);
        when(exposures.getExposureCount()).thenReturn(1_000L);
        when(exposureRepository.countExposuresByPlaceIds(List.of(10L, 20L))).thenReturn(List.of(exposures));
        PlaceRecommendationClickRepository.PlaceClickCountProjection clicks = mock(
                PlaceRecommendationClickRepository.PlaceClickCountProjection.class);
        when(clicks.getClickCount()).thenReturn(200L);
        when(clickRepository.countClicksByPlaceIds(List.of(10L, 20L))).thenReturn(List.of(clicks));
        MapBookmarkRepository.PlaceBookmarkCountProjection bookmarks = mock(
                MapBookmarkRepository.PlaceBookmarkCountProjection.class);
        when(bookmarks.getBookmarkCount()).thenReturn(50L);
        when(bookmarkRepository.findBookmarkCountsByPlaceIds(List.of(10L, 20L))).thenReturn(List.of(bookmarks));
        when(reservationRepository.countOwnedByMerchantOwnerUserId(7L)).thenReturn(40L);
        when(reservationRepository.countOwnedByMerchantOwnerUserIdAndStatus(7L, ReservationStatus.CONFIRMED))
                .thenReturn(30L);

        var response = service.get(7L);

        assertThat(response.placeCount()).isEqualTo(2);
        assertThat(response.exposureCount()).isEqualTo(1_000L);
        assertThat(response.clickCount()).isEqualTo(200L);
        assertThat(response.bookmarkCount()).isEqualTo(50L);
        assertThat(response.reservationCount()).isEqualTo(40L);
        assertThat(response.confirmedReservationCount()).isEqualTo(30L);
        assertThat(response.clickThroughRate()).isEqualTo(20.0);
        assertThat(response.reservationConversionRate()).isEqualTo(15.0);
        verify(accessPolicy).requireActiveMerchantOwner(eq(7L), any());
    }

    @Test
    void returnsZeroRatesWhenThereIsNoConversionDenominator() {
        when(ownerPlaceRepository.findAllByMerchantOwnerUserIdOrderByPlaceIdAsc(7L)).thenReturn(List.of());
        when(reservationRepository.countOwnedByMerchantOwnerUserId(7L)).thenReturn(0L);
        when(reservationRepository.countOwnedByMerchantOwnerUserIdAndStatus(7L, ReservationStatus.CONFIRMED))
                .thenReturn(0L);

        var response = service.get(7L);

        assertThat(response.placeCount()).isZero();
        assertThat(response.clickThroughRate()).isZero();
        assertThat(response.reservationConversionRate()).isZero();
        verifyNoInteractions(exposureRepository, clickRepository, bookmarkRepository);
    }
}
