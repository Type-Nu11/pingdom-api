package com.typenull.pingdom.place.api.dto.registration;

import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 사업자 검증과 신규 장소 등록 또는 기존 장소 소유권 신청을 한 번에 받는 Web 입력 계약입니다. */
public record MerchantPlaceApplicationRequest(
        @NotNull MerchantPlaceApplicationType applicationType,
        @NotBlank @Size(max = 100) String legalName,
        @NotBlank @Size(max = 100) String businessName,
        @NotBlank @Size(max = 30) String businessRegistrationNumber,
        @NotBlank @Size(max = 100) String merchantDisplayName,
        @Size(max = 1000) String merchantDescription,
        @NotBlank @Email @Size(max = 255) String merchantContactEmail,
        @NotBlank @Size(max = 30) String merchantContactPhone,
        @Valid PlaceRegistrationRequest newPlace,
        @Positive Long existingPlaceId,
        @Size(max = 500) String claimReason,
        /**
         * 기존 장소 Claim에서는 더 이상 사용하지 않습니다. 실제 파일은 multipart 첨부 API로만 등록합니다.
         * NEW_PLACE의 하위 newPlace.attachments 계약에는 영향을 주지 않습니다.
         */
        @Valid @Size(max = 20) List<PlaceRegistrationAttachmentRequest> attachments
) {
}
