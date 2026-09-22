package com.typenull.pingdom.identity.infrastructure.crypto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 사업자 검증 정보용 Base64 암호화 키 설정입니다.
 * 공백 여부는 설정 검증에서, 디코딩 가능 여부와 32바이트 길이는 cipher 생성 시 확인합니다.
 */
@Validated
@ConfigurationProperties(prefix = "merchant.verification")
public record MerchantVerificationEncryptionProperties(
        @NotBlank(message = "Merchant 검증 암호화 키는 필수입니다.")
        String encryptionKey
) {
}
