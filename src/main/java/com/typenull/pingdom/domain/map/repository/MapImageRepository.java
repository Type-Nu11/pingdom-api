package com.typenull.pingdom.domain.map.repository;

import com.typenull.pingdom.domain.map.domain.MapImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapImageRepository extends JpaRepository<MapImage,Long> {
}
