package com.typenull.pingdom.place.api.dto.place.detail;

import com.typenull.pingdom.availability.api.dto.AvailabilityResponse;
import com.typenull.pingdom.offer.api.dto.OfferPageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 방문 판단에 필요한 장소·사업자 연락처·진행 행사·예약 가능 시간·혜택을 조합한 응답입니다.
 * 예약 가능 정보는 조회 결과이며 응답 생성만으로 재고나 예약 자리를 확보하지 않습니다.
 */
@Schema(description = "관광객 장소 상세 방문 결정 화면 응답")
public record PlaceVisitDecisionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PlaceDetailResponse place,
        @Schema(
                nullable = true,
                description = "활성 Merchant Owner가 연결된 경우에만 제공",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        PlaceVisitDecisionMerchantInformationResponse merchantInformation,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<PlaceVisitDecisionEventResponse> ongoingEvents,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<AvailabilityResponse> reservableAvailabilities,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        OfferPageResponse availableOffers,
        @Schema(description = "응답의 상태성 데이터를 조회한 시각", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime checkedAt
) {
}
