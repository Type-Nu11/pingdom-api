package com.typenull.pingdom.place.api.dto.place.detail;

import com.typenull.pingdom.availability.api.dto.AvailabilityResponse;
import com.typenull.pingdom.offer.api.dto.OfferPageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "관광객 장소 상세 방문 결정 화면 응답")
public record PlaceVisitDecisionResponse(
        PlaceDetailResponse place,
        @Schema(nullable = true, description = "활성 Merchant Owner가 연결된 경우에만 제공")
        PlaceVisitDecisionMerchantInformationResponse merchantInformation,
        List<PlaceVisitDecisionEventResponse> ongoingEvents,
        List<AvailabilityResponse> reservableAvailabilities,
        OfferPageResponse availableOffers,
        @Schema(description = "응답의 상태성 데이터를 조회한 시각") LocalDateTime checkedAt
) {
}
