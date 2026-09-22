package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.media.PlaceMedia;
import com.typenull.pingdom.place.domain.place.media.PlaceMediaPurpose;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 장소·목적별 미디어 순서와 원천별 승격 여부를 조회합니다.
 * 순서 일괄 이동은 먼저 flush한 뒤 영속성 컨텍스트를 비우므로 호출자는 이동 이후 엔티티를 다시 조회해야 합니다.
 */
public interface PlaceMediaRepository extends JpaRepository<PlaceMedia, Long> {

    List<PlaceMedia> findAllByPlace_IdAndPurposeOrderByDisplayOrderAscIdAsc(
            Long placeId,
            PlaceMediaPurpose purpose
    );

    Optional<PlaceMedia> findBySourceMapImageId(Long sourceMapImageId);

    Optional<PlaceMedia> findBySourceRegistrationAttachmentId(Long sourceRegistrationAttachmentId);

    Optional<PlaceMedia> findByIdAndPlace_IdAndPurpose(Long id, Long placeId, PlaceMediaPurpose purpose);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE PlaceMedia media
            SET media.displayOrder = media.displayOrder + :offset
            WHERE media.place.id = :placeId
              AND media.purpose = :purpose
            """)
    int increaseDisplayOrder(
            @Param("placeId") Long placeId,
            @Param("purpose") PlaceMediaPurpose purpose,
            @Param("offset") int offset
    );

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
