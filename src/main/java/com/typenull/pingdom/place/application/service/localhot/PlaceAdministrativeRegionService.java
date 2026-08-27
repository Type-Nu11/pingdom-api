package com.typenull.pingdom.place.application.service.localhot;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegion;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegionResolver;
import com.typenull.pingdom.place.domain.place.region.ResolvedPlaceAdministrativeRegion;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceAdministrativeRegionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceAdministrativeRegionService {

    private final PlaceAdministrativeRegionResolver resolver;
    private final PlaceAdministrativeRegionRepository regionRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final Clock clock;

    @Transactional
    public boolean synchronizeIfConfigured(MapPlace place) {
        if (!resolver.isConfigured()) {
            return false;
        }
        synchronize(place);
        return true;
    }

    @Transactional
    public boolean synchronizeByIdIfConfigured(long placeId) {
        if (!resolver.isConfigured()) {
            return false;
        }
        MapPlace place = mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new IllegalArgumentException("지역을 갱신할 장소가 없습니다. placeId=" + placeId));
        synchronize(place);
        return true;
    }

    public void synchronize(MapPlace place) {
        ResolvedPlaceAdministrativeRegion region = resolver.resolve(place.getLatitude(), place.getLongitude());
        LocalDateTime now = LocalDateTime.now(clock);
        regionRepository.findById(region.code())
                .ifPresentOrElse(
                        existing -> existing.refresh(region, now),
                        () -> regionRepository.save(PlaceAdministrativeRegion.from(region, now))
                );
        place.updateAdministrativeRegion(region.code());
    }
}
