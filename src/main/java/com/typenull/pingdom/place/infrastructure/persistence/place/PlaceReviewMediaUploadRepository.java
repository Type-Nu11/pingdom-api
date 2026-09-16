package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.review.PlaceReviewMediaUpload;
import com.typenull.pingdom.place.domain.review.PlaceReviewMediaUploadStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceReviewMediaUploadRepository extends JpaRepository<PlaceReviewMediaUpload, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT media FROM PlaceReviewMediaUpload media WHERE media.id = :mediaId")
    Optional<PlaceReviewMediaUpload> findByIdForUpdate(@Param("mediaId") Long mediaId);

    List<PlaceReviewMediaUpload> findAllByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
            PlaceReviewMediaUploadStatus status,
            LocalDateTime expiresAt,
            Pageable pageable
    );
}
