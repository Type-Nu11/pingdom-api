package com.typenull.pingdom.identity.api.dto.merchant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MerchantVerificationRequest(
        @NotBlank @Size(max = 100) String legalName,
        @NotBlank
        @Pattern(
                regexp = "\\d{3}-?\\d{2}-?\\d{5}",
                message = "사업자등록번호는 숫자 10자리 형식이어야 합니다."
        )
        String businessRegistrationNumber
) {
    public String normalizedLegalName() {
        return legalName.trim();
    }

    public String normalizedBusinessRegistrationNumber() {
        return businessRegistrationNumber.replace("-", "");
    }
}
