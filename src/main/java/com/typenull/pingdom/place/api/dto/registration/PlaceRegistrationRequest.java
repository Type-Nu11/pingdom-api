package com.typenull.pingdom.place.api.dto.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlaceRegistrationRequest(
        @NotBlank @Size(max = 100) String placeName,
        @NotNull PlaceRegistrationCategory category,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotBlank @Size(max = 255) String roadAddress,
        @NotBlank @Size(max = 255) String jibunAddress,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 1000) String description,
        String businessRegistrationFileId,
        String identityDocumentFileId,
        String representativeImageFileIds
) {
}
