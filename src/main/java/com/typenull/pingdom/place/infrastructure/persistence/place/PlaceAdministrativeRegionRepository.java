package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceAdministrativeRegionRepository extends JpaRepository<PlaceAdministrativeRegion, String> {
}
