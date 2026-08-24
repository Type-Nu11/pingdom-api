package com.typenull.pingdom.place.infrastructure.persistence.place;
import com.typenull.pingdom.place.domain.review.PlaceReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PlaceReviewRepository extends JpaRepository<PlaceReview, Long> { Page<PlaceReview> findAllByPlace_Id(Long placeId, Pageable pageable); }
