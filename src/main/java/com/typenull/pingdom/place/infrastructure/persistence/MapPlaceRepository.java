package com.typenull.pingdom.place.infrastructure.persistence;

import org.springframework.data.domain.Page;
import com.typenull.pingdom.place.domain.MapPlace;

import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MapPlaceRepository extends JpaRepository<MapPlace, Long> {
    Optional<MapPlace> findByKakaoPlaceId(String kakaoPlaceId);
    boolean existsByKakaoPlaceId(String kakaoPlaceId);
    @Query("SELECT m FROM MapPlace m WHERE (:keyword = '' OR m.name LIKE %:keyword%)")
    Page<MapPlace> findByNameContaining(String keyword, PageRequest pageRequest);
}
