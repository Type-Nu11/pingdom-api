package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.media.PlaceMedia;
import com.typenull.pingdom.place.domain.place.media.PlaceMediaPurpose;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceMediaRepository extends JpaRepository<PlaceMedia, Long> {

    List<PlaceMedia> findAllByPlace_IdAndPurposeOrderByDisplayOrderAscIdAsc(
            Long placeId,
            PlaceMediaPurpose purpose
    );

    Optional<PlaceMedia> findBySourceMapImageId(Long sourceMapImageId);

    Optional<PlaceMedia> findBySourceRegistrationAttachmentId(Long sourceRegistrationAttachmentId);

    Optional<PlaceMedia> findByIdAndPlace_IdAndPurpose(Long id, Long placeId, PlaceMediaPurpose purpose);

    @Query("""
            SELECT COALESCE(MAX(media.displayOrder), -1)
            FROM PlaceMedia media
            WHERE media.place.id = :placeId
              AND media.purpose = :purpose
            """)
    int findMaxDisplayOrder(
            @Param("placeId") Long placeId,
            @Param("purpose") PlaceMediaPurpose purpose
    );
}
