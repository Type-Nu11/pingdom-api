package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.media.PlaceMedia;
import com.typenull.pingdom.place.domain.place.media.PlaceMediaPurpose;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceMediaRepository extends JpaRepository<PlaceMedia, Long> {

    List<PlaceMedia> findAllByPlace_IdAndPurposeOrderByDisplayOrderAscIdAsc(
            Long placeId,
            PlaceMediaPurpose purpose
    );

    Optional<PlaceMedia> findBySourceMapImageId(Long sourceMapImageId);
}
