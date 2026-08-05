package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.api.dto.place.detail.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.place.detail.PlaceVisitDecisionResponse;
import com.typenull.pingdom.place.api.dto.place.autocomplete.PlaceAutocompleteResponse;
import com.typenull.pingdom.place.api.dto.place.list.PlaceListResponse;
import com.typenull.pingdom.place.api.dto.place.card.TouristPlaceCardResponse;

public interface PlaceQueryService {
    PlaceListResponse listPlaces(PlaceSearchCondition condition);

    PlaceAutocompleteResponse autocompletePlaces(String keyword, int limit, Double latitude, Double longitude);

    PlaceDetailResponse getPlace(Long placeId);

    PlaceVisitDecisionResponse getPlaceVisitDecision(Long placeId);

    TouristPlaceCardResponse getTouristPlaceCard(Long placeId);

    PlaceListResponse listBookmarkedPlaces(Long userId, int page, int limit);
}
