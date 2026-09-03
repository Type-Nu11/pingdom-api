package com.typenull.pingdom.place.api.dto.review;
import com.typenull.pingdom.place.domain.review.PlaceReview;
import java.time.LocalDateTime; import java.util.List;
public record PlaceReviewResponse(Long reviewId, Long placeId, Long userId, String recommendReason, String content, List<String> imageUrls, LocalDateTime createdAt) {
 public static PlaceReviewResponse from(PlaceReview r){return new PlaceReviewResponse(r.getId(),r.getPlace().getId(),r.getUserId(),r.getRecommendReason(),r.getContent(),List.copyOf(r.getImageUrls()),r.getCreatedAt());}}
