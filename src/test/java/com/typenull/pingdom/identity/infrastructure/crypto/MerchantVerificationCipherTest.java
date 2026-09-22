package com.typenull.pingdom.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MerchantVerificationCipherTest {

    private static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final MerchantVerificationCipher cipher = new MerchantVerificationCipher(
            new MerchantVerificationEncryptionProperties(TEST_KEY)
    );

    /**
     * 사업자번호 암호문이 v1 접두사를 사용하고 평문을 포함하지 않으며 복호화로 원문이 복원되는지 검증한다.
     */
    @Test
    void encryptsAndDecryptsRegistrationNumber() {
        String encrypted = cipher.encrypt("1234567890");

        assertThat(encrypted).startsWith("v1:").doesNotContain("1234567890");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("1234567890");
    }

    /**
     * 암호문의 마지막 문자를 바꾼 입력을 복호화하면 IllegalStateException으로 거절되는지 검증한다.
     */
    @Test
    void rejectsTamperedCiphertext() {
        String encrypted = cipher.encrypt("1234567890");
        String tampered = encrypted.substring(0, encrypted.length() - 1) + "A";

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 선택 값이 없는 경우 암호화·복호화 모두 null을 그대로 유지하는지 검증한다.
     */
    @Test
    void preservesNullValues() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    /**
     * 지원하지 않는 v2 암호문 접두사는 IllegalStateException으로 거절되는지 검증한다.
     */
    @Test
    void rejectsUnsupportedCiphertextVersion() {
        assertThatThrownBy(() -> cipher.decrypt("v2:payload"))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * v1 접두사라도 잘못된 Base64 payload는 IllegalStateException으로 거절되는지 검증한다.
     */
    @Test
    void rejectsMalformedBase64Payload() {
        assertThatThrownBy(() -> cipher.decrypt("v1:not-base64!"))
                .isInstanceOf(IllegalStateException.class);
    }
}
