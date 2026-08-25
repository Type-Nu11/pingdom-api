package com.typenull.pingdom.place.infrastructure.persistence.place;
import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewVisibilityStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface PlaceReviewRepository extends JpaRepository<PlaceReview, Long> {
    Page<PlaceReview> findAllByPlace_IdAndVisibilityStatus(Long placeId, PlaceReviewVisibilityStatus visibilityStatus, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT review FROM PlaceReview review WHERE review.id = :reviewId AND review.place.id = :placeId")
    Optional<PlaceReview> findByIdAndPlaceIdForUpdate(@Param("reviewId") Long reviewId, @Param("placeId") Long placeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT review FROM PlaceReview review WHERE review.id = :reviewId")
    Optional<PlaceReview> findByIdForUpdate(@Param("reviewId") Long reviewId);
}
