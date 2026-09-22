package com.typenull.pingdom.place.api.dto.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationCategory;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationTag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.List;

/**
 * 신규 장소 초안의 입력값. 위도·경도는 도 단위이며 전화번호는 서비스에서 국제 형식으로 검증.
 * 시간대 누락은 Asia/Seoul, tags 누락은 빈 집합이며 영업일은 서비스에서 7개 요일 전체를 요구.
 */
public record PlaceRegistrationRequest(
        @NotBlank @Size(max = 100) String placeName,
        @NotNull PlaceRegistrationCategory category,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotBlank @Size(max = 255) String roadAddress,
        @NotBlank @Size(max = 255) String jibunAddress,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 1000) String description,
        @NotBlank @Size(max = 20) String businessContactPhone,
        @NotBlank @Size(max = 20) String applicantContactPhone,
        Set<PlaceRegistrationTag> tags,
        @Size(max = 64) String timezone,
        @Valid @Size(max = 7) List<PlaceRegistrationOperatingDay> operatingDays
) {
}
