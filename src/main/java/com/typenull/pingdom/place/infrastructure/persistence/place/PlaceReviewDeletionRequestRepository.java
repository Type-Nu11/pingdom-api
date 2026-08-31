package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequest;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceReviewDeletionRequestRepository extends JpaRepository<PlaceReviewDeletionRequest, Long> {

    boolean existsByReview_IdAndStatus(Long reviewId, PlaceReviewDeletionRequestStatus status);

    Page<PlaceReviewDeletionRequest> findAllByStatus(PlaceReviewDeletionRequestStatus status, Pageable pageable);

    List<PlaceReviewDeletionRequest> findAllByReview_IdInOrderByReview_IdAscCreatedAtDescIdDesc(
            Collection<Long> reviewIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM PlaceReviewDeletionRequest request WHERE request.id = :requestId")
    Optional<PlaceReviewDeletionRequest> findByIdForUpdate(@Param("requestId") Long requestId);
}
