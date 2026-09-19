package com.typenull.pingdom.place.api.dto.registration;

import java.util.List;

public record NaverPlaceSearchResponse(List<Item> items) {
    public record Item(String name, String roadAddress, String jibunAddress, double latitude, double longitude) {}
}
