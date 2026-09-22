package com.typenull.pingdom.place.api.dto.registration;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 신규 장소 신청 화면이 주소 선택 뒤 입력 필드에 채울 Geocoding 후보 목록입니다. */
@Schema(description = "신규 장소 등록용 네이버 주소 검색 결과")
public record NaverAddressSearchResponse(
        @Schema(description = "주소 검색 후보 목록. 검색 결과가 없으면 빈 목록입니다.") List<Item> items
) {
    /** 도로명·지번 주소와 선택 우편번호, WGS84 좌표를 함께 전달하는 주소 후보입니다. */
    public record Item(
            @Schema(description = "도로명 주소", example = "경기도 성남시 분당구 불정로 6") String roadAddress,
            @Schema(description = "지번 주소", example = "경기도 성남시 분당구 정자동 178-1") String jibunAddress,
            @Schema(description = "우편번호. 제공되지 않는 후보는 null입니다.", example = "13561", nullable = true) String postalCode,
            @Schema(description = "WGS84 위도", example = "37.3595963") double latitude,
            @Schema(description = "WGS84 경도", example = "127.1054328") double longitude
    ) {
    }
}
