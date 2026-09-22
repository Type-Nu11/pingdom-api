package com.typenull.pingdom.place.application.service.recommendation.query;

import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 같은 조회 시각에 이용 가능한 오퍼와 예약 재고가 있는 장소를 일괄 조회합니다.
 * 아무 신호도 없는 장소는 결과 맵에 넣지 않으며 이 조회는 혜택 발급이나 예약 재고 점유를 수행하지 않습니다.
 */
@Component
class PlaceRecommendationCommerceSignalLoader {

    private final TouristOfferRepository touristOfferRepository;
    private final PlaceAvailabilityRepository placeAvailabilityRepository;
    private final Clock clock;

    @Autowired
    PlaceRecommendationCommerceSignalLoader(
            TouristOfferRepository touristOfferRepository,
            PlaceAvailabilityRepository placeAvailabilityRepository
    ) {
        this(touristOfferRepository, placeAvailabilityRepository, Clock.systemUTC());
    }

    PlaceRecommendationCommerceSignalLoader(
            TouristOfferRepository touristOfferRepository,
            PlaceAvailabilityRepository placeAvailabilityRepository,
            Clock clock
    ) {
        this.touristOfferRepository = touristOfferRepository;
        this.placeAvailabilityRepository = placeAvailabilityRepository;
        this.clock = clock;
    }

    Map<Long, CommerceSignal> load(Collection<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Set<Long> benefitPlaceIds = new HashSet<>(
                touristOfferRepository.findPlaceIdsWithAvailableOffers(placeIds, now)
        );
        Set<Long> reservablePlaceIds = new HashSet<>(
                placeAvailabilityRepository.findPlaceIdsWithReservableAvailability(placeIds, now)
        );

        Set<Long> signaledPlaceIds = new HashSet<>(benefitPlaceIds);
        signaledPlaceIds.addAll(reservablePlaceIds);
        return signaledPlaceIds.stream().collect(Collectors.toUnmodifiableMap(
                Function.identity(),
                placeId -> new CommerceSignal(
                        benefitPlaceIds.contains(placeId),
                        reservablePlaceIds.contains(placeId)
                )
        ));
    }

    record CommerceSignal(boolean activeBenefit, boolean reservable) {
        static final CommerceSignal NONE = new CommerceSignal(false, false);
    }
}
