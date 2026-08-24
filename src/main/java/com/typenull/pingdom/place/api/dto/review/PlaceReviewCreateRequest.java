package com.typenull.pingdom.place.api.dto.review;
import jakarta.validation.constraints.*;
import java.util.List;
public record PlaceReviewCreateRequest(@NotBlank @Size(max=100) String recommendReason, @NotBlank @Size(max=2000) String content, @Size(max=3) List<@NotBlank @Size(max=500) String> imageUrls) { }
