package com.typenull.pingdom.place.api.dto.registration;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "신규 장소 등록용 네이버 업체명 검색 결과")
public record NaverPlaceSearchResponse(
        @ArraySchema(arraySchema = @Schema(description = "장소 검색 후보 목록. 검색 결과가 없으면 빈 목록입니다."),
                maxItems = 5, schema = @Schema(implementation = Item.class)) List<Item> items
) {
    @Schema(name = "NaverPlaceSearchItem", description = "네이버 업체명 검색 후보")
    public record Item(
            @Schema(description = "HTML 태그를 제거한 장소명", example = "네이버 본사") String name,
            @Schema(description = "도로명 주소", example = "경기도 성남시 분당구 불정로 6") String roadAddress,
            @Schema(description = "지번 주소", example = "경기도 성남시 분당구 정자동 178-1") String jibunAddress,
            @Schema(description = "WGS84 위도", example = "37.3595963") double latitude,
            @Schema(description = "WGS84 경도", example = "127.1054328") double longitude
    ) {}
}
