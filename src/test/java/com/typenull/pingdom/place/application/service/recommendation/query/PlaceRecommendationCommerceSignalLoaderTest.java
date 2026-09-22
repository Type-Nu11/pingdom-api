package com.typenull.pingdom.place.application.service.recommendation.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceRecommendationCommerceSignalLoaderTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-23T10:00:00Z"),
            ZoneOffset.UTC
    );
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 23, 10, 0);

    @Mock
    private TouristOfferRepository touristOfferRepository;

    @Mock
    private PlaceAvailabilityRepository placeAvailabilityRepository;

    private PlaceRecommendationCommerceSignalLoader loader;

    /**
     * 혜택과 예약을 같은 고정 시각에 조회하는 로더를 준비합니다.
     */
    @BeforeEach
    void setUp() {
        loader = new PlaceRecommendationCommerceSignalLoader(
                touristOfferRepository,
                placeAvailabilityRepository,
                CLOCK
        );
    }

    /**
     * 혜택만·예약만·두 가지 모두 있는 장소를 구분하고 두 저장소가 같은 기준 시각을 받는지 확인합니다.
     */
    @Test
    void combinesCommerceSignalsAtSameTime() {
        List<Long> placeIds = List.of(1L, 2L, 3L);
        when(touristOfferRepository.findPlaceIdsWithAvailableOffers(placeIds, NOW))
                .thenReturn(List.of(1L, 3L));
        when(placeAvailabilityRepository.findPlaceIdsWithReservableAvailability(placeIds, NOW))
                .thenReturn(List.of(2L, 3L));

        Map<Long, PlaceRecommendationCommerceSignalLoader.CommerceSignal> signals = loader.load(placeIds);

        assertThat(signals.get(1L)).isEqualTo(
                new PlaceRecommendationCommerceSignalLoader.CommerceSignal(true, false)
        );
        assertThat(signals.get(2L)).isEqualTo(
                new PlaceRecommendationCommerceSignalLoader.CommerceSignal(false, true)
        );
        assertThat(signals.get(3L)).isEqualTo(
                new PlaceRecommendationCommerceSignalLoader.CommerceSignal(true, true)
        );
        verify(touristOfferRepository).findPlaceIdsWithAvailableOffers(placeIds, NOW);
        verify(placeAvailabilityRepository).findPlaceIdsWithReservableAvailability(placeIds, NOW);
    }

    /**
     * 후보가 비어 있으면 빈 맵을 반환하고 혜택·예약 조회를 생략하는지 확인합니다.
     */
    @Test
    void skipsEmptyCommerceCandidates() {
        assertThat(loader.load(List.of())).isEmpty();

        verifyNoInteractions(touristOfferRepository, placeAvailabilityRepository);
    }
}
