package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.api.dto.place.detail.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.place.detail.PlaceVisitDecisionResponse;
import com.typenull.pingdom.place.api.dto.place.autocomplete.PlaceAutocompleteResponse;
import com.typenull.pingdom.place.api.dto.place.list.PlaceListResponse;
import com.typenull.pingdom.place.api.dto.place.card.TouristPlaceCardResponse;
import com.typenull.pingdom.place.api.dto.place.reservable.NearbyReservablePlaceResponse;

/**
 * 검색·자동완성·상세·방문 판단·북마크 목록을 공개 DTO로 제공하는 조회 계약.
 * 각 조회의 노출·운영 상태 필터가 다를 수 있으므로 구현의 개별 조회 조건을 따름.
 */
public interface PlaceQueryService {
    PlaceListResponse listPlaces(PlaceSearchCondition condition);

    NearbyReservablePlaceResponse listNearbyReservablePlaces(NearbyReservablePlaceCondition condition);

    PlaceAutocompleteResponse autocompletePlaces(String keyword, int limit, Double latitude, Double longitude);

    PlaceDetailResponse getPlace(Long placeId);

    PlaceVisitDecisionResponse getPlaceVisitDecision(Long placeId);

    TouristPlaceCardResponse getTouristPlaceCard(Long placeId);

    PlaceListResponse listBookmarkedPlaces(Long userId, int page, int limit);
}
