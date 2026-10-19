package com.typenull.pingdom.identity.api.dto.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class MerchantOwnerMediaUploadRequestValidationTest {

    /** S3 직접 업로드 URL 요청도 서버 파일 검증과 동일한 20MiB 경계를 허용. */
    @Test
    void acceptsExactlyTwentyMiB() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = new MerchantOwnerMediaUploadRequest("camera.jpg", "image/jpeg", 20_971_520L);

            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    @Test
    void rejectsMoreThanTwentyMiB() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = new MerchantOwnerMediaUploadRequest("camera.jpg", "image/jpeg", 20_971_521L);

            assertThat(factory.getValidator().validate(request))
                    .singleElement().satisfies(violation ->
                            assertThat(violation.getPropertyPath().toString()).isEqualTo("fileSize"));
        }
    }
}
