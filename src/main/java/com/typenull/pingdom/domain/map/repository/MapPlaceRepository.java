package com.typenull.pingdom.domain.map.repository;

import com.typenull.pingdom.domain.map.domain.MapPlace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapPlaceRepository extends JpaRepository<MapPlace, Long> {
}

