package com.typenull.pingdom.identity.infrastructure.crypto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "merchant.verification")
public record MerchantVerificationEncryptionProperties(
        @NotBlank(message = "Merchant 검증 암호화 키는 필수입니다.")
        String encryptionKey
) {
}
