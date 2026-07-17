package com.typenull.pingdom.identity.infrastructure.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class MerchantVerificationCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String VERSION_PREFIX = "v1:";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int AUTH_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public MerchantVerificationCipher(MerchantVerificationEncryptionProperties properties) {
        byte[] decodedKey;
        try {
            decodedKey = Base64.getDecoder().decode(properties.encryptionKey());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Merchant 검증 암호화 키는 Base64 형식이어야 합니다.", exception);
        }
        if (decodedKey.length != 32) {
            throw new IllegalStateException("Merchant 검증 암호화 키는 32바이트여야 합니다.");
        }
        this.secretKey = new SecretKeySpec(decodedKey, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(AUTH_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Merchant 검증 정보를 암호화할 수 없습니다.", exception);
        }
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null) {
            return null;
        }
        if (!encryptedValue.startsWith(VERSION_PREFIX)) {
            throw new IllegalStateException("지원하지 않는 Merchant 검증 암호문 형식입니다.");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedValue.substring(VERSION_PREFIX.length()));
            if (payload.length <= IV_LENGTH_BYTES) {
                throw new IllegalStateException("Merchant 검증 암호문이 손상되었습니다.");
            }
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[payload.length - IV_LENGTH_BYTES];
            System.arraycopy(payload, 0, iv, 0, iv.length);
            System.arraycopy(payload, iv.length, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(AUTH_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Merchant 검증 정보를 복호화할 수 없습니다.", exception);
        }
    }
}
