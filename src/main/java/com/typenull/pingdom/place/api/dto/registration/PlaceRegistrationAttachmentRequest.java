package com.typenull.pingdom.place.api.dto.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlaceRegistrationAttachmentRequest(
        String fileId,
        @NotNull PlaceRegistrationAttachmentType documentType,
        @NotBlank @Size(max = 500) String storageKey,
        @NotBlank @Size(max = 255) String originalFilename,
        @NotBlank @Size(max = 100) String contentType,
        @Positive long fileSize,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{64}") String fileHash,
        @PositiveOrZero Integer displayOrder,
        @Positive Integer retentionDays
) {
    public int resolvedDisplayOrder() {
        return displayOrder == null ? 0 : displayOrder;
    }
}
