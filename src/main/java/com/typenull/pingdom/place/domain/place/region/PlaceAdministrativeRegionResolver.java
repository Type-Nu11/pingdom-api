package com.typenull.pingdom.place.domain.place.region;

public interface PlaceAdministrativeRegionResolver {

    boolean isConfigured();

    ResolvedPlaceAdministrativeRegion resolve(double latitude, double longitude);
}
