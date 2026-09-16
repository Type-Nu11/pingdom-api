package com.typenull.pingdom.place.api.dto.review;
import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewRecommendReason;
import java.time.LocalDateTime; import java.util.List;
public record PlaceReviewResponse(Long reviewId, Long placeId, Long userId, String recommendReason, List<PlaceReviewRecommendReason> recommendReasons, String content, List<String> imageUrls, List<PlaceReviewMediaResponse> reviewMedia, LocalDateTime createdAt) {
 public static PlaceReviewResponse from(PlaceReview r){return new PlaceReviewResponse(r.getId(),r.getPlace().getId(),r.getUserId(),r.getRecommendReason(),copyReasons(r),r.getContent(),List.copyOf(r.getImageUrls()),copyMedia(r),r.getCreatedAt());}
 private static List<PlaceReviewRecommendReason> copyReasons(PlaceReview review) { return review.getRecommendReasons() == null ? List.of() : List.copyOf(review.getRecommendReasons()); }
 private static List<PlaceReviewMediaResponse> copyMedia(PlaceReview review) { return review.getMediaUploads() == null ? List.of() : review.getMediaUploads().stream().map(PlaceReviewMediaResponse::from).toList(); }}
