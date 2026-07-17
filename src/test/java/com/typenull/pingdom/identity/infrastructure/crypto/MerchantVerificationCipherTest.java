package com.typenull.pingdom.identity.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MerchantVerificationCipherTest {

    private static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final MerchantVerificationCipher cipher = new MerchantVerificationCipher(
            new MerchantVerificationEncryptionProperties(TEST_KEY)
    );

    @Test
    void encryptsAndDecryptsRegistrationNumber() {
        String encrypted = cipher.encrypt("1234567890");

        assertThat(encrypted).startsWith("v1:").doesNotContain("1234567890");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("1234567890");
    }

    @Test
    void rejectsTamperedCiphertext() {
        String encrypted = cipher.encrypt("1234567890");
        String tampered = encrypted.substring(0, encrypted.length() - 1) + "A";

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void preservesNullValues() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void rejectsUnsupportedCiphertextVersion() {
        assertThatThrownBy(() -> cipher.decrypt("v2:payload"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMalformedBase64Payload() {
        assertThatThrownBy(() -> cipher.decrypt("v1:not-base64!"))
                .isInstanceOf(IllegalStateException.class);
    }
}
