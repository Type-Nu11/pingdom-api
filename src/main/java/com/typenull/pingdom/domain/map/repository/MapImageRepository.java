package com.typenull.pingdom.domain.map.repository;

import com.typenull.pingdom.domain.map.domain.MapImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MapImageRepository extends JpaRepository<MapImage,Long> {
}
