package com.typenull.pingdom.place.infrastructure.persistence;

import com.typenull.pingdom.place.domain.MapPlace;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapPlaceRepository extends JpaRepository<MapPlace, Long> {
    Optional<MapPlace> findByKakaoPlaceId(String kakaoPlaceId);
    boolean existsByKakaoPlaceId(String kakaoPlaceId);
}
