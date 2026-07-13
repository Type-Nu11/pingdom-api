package com.typenull.pingdom.identity.api.dto.merchant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MerchantOwnerProfileRequest(
        @NotBlank @Size(max = 100) String businessName,
        @NotBlank @Size(max = 100) String displayName,
        @Size(max = 1000) String description,
        @NotBlank @Email @Size(max = 255) String contactEmail,
        @NotBlank @Size(max = 30) String contactPhone
) {
}
