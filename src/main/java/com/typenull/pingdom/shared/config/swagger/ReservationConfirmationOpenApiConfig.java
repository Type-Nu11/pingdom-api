package com.typenull.pingdom.shared.config.swagger;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReservationConfirmationOpenApiConfig {
    private static final String QUOTE = "/places/{placeId}/availabilities/{availabilityId}/quote";

    @Bean
    public GlobalOpenApiCustomizer reservationConfirmationContract() {
        return api -> {
            if (api.getPaths() == null) return;
            if (api.getPaths().get(QUOTE) != null) {
                Operation quote = api.getPaths().get(QUOTE).getGet();
                errors(quote);
                error(quote, "404", "RESERVATION_SLOT_NOT_FOUND: 슬롯 부재/장소 불일치. 재확인이 필요합니다.");
                error(quote, "403", "TOURIST_ACCOUNT_REQUIRED: 활성 일반 사용자만 견적을 조회할 수 있습니다.");
                error(quote, "429", "QUOTE_RATE_LIMITED: 사용자당 시간당 60건 또는 미사용 견적 120건 한도 초과. 잠시 후 다시 조회하세요.");
            }
            if (api.getPaths().get("/reservations") != null) {
                Operation create = api.getPaths().get("/reservations").getPost();
                if (create != null) {
                    create.setDescription("confirmationToken을 제공하면 확인한 대상·시간·인원·금액·정책을 원자적으로 검증합니다. "
                            + "토큰 생략은 기존 터치 계약이며 확인 조건 보장은 없습니다. 성공은 예약 생성(PENDING)이며 결제 성공이 아닙니다. "
                            + "응답 유실에는 동일 키·토큰·본문을 재전송하세요. 사용자별 키는 예약 또는 거절 기록이 보존되는 동안 만료하지 않습니다. "
                            + "동일 요청의 성공은 견적 만료 후에도 201로 복구하며, 확정 거절은 같은 오류로 복구합니다. "
                            + "timeout/5xx는 결과 불명이므로 실패로 확정하거나 새 키를 만들지 않습니다. 조건 재확인 후 새 의도는 새 토큰·키로 제출합니다.");
                    errors(create);
                    error(create, "404", "QUOTE_NOT_FOUND: 본인 견적 부재. RESERVATION_SLOT_NOT_FOUND: 슬롯 부재. "
                            + "AVAILABILITY_NOT_FOUND: 기존 터치 요청의 예약 가능 시간 부재.");
                    error(create, "410", "QUOTE_EXPIRED: 견적 만료. 최신 조건과 새 토큰으로 재확인이 필요합니다.");
                    error(create, "409", "IDEMPOTENCY_KEY_REUSED: 같은 키/토큰의 다른 요청. QUOTE_REQUEST_MISMATCH: 대상·인원 불일치. "
                            + "QUOTE_CONDITIONS_CHANGED, RESERVATION_SLOT_INACTIVE, RESERVATION_PRODUCT_UNAVAILABLE, RESERVATION_CAPACITY_EXCEEDED: 최신 조건을 다시 확인해야 합니다. "
                            + "AVAILABILITY_CAPACITY_EXCEEDED: 기존 터치 요청의 정원 부족.");
                }
            }
            if (api.getPaths().get("/reservations/{reservationId}/cancel") != null) {
                error(api.getPaths().get("/reservations/{reservationId}/cancel").getPost(), "409",
                        "CANCELLATION_NOT_ALLOWED: 취소 불가 또는 기한 경과. RESERVATION_REFUND_REQUIRED: 처리 중 결제/미환불 결제. INVALID_RESERVATION_STATE: 확정되지 않은 예약.");
            }
            if (api.getPaths().get("/payments") != null) {
                error(api.getPaths().get("/payments").getPost(), "503", "PROVIDER_RESULT_UNKNOWN: 공급자 결과 불명. "
                        + "PAYMENT_QUOTE_MISMATCH: 수락한 견적과 결제 금액/통화 불일치. 처리 중 상태를 유지하며 같은 키로 복구/운영 확인해야 합니다.");
            }
        };
    }

    private void errors(Operation operation) {
        error(operation, "404", "QUOTE_NOT_FOUND: 본인 견적 부재. RESERVATION_SLOT_NOT_FOUND: 슬롯 부재/장소 불일치. 재확인이 필요합니다.");
        error(operation, "409", "QUOTE_CONDITIONS_CHANGED: 조건 변경. RESERVATION_SLOT_INACTIVE: 비활성/시작된 슬롯. "
                + "RESERVATION_PRODUCT_UNAVAILABLE: 잘못된 상품 연결. RESERVATION_CAPACITY_EXCEEDED: 정원 부족. 재확인이 필요합니다.");
        error(operation, "422", "QUOTE_TERMS_UNAVAILABLE: 가격/취소 정책 미설정 또는 계산 불가. 무료/취소 불가로 추정하지 않습니다.");
    }

    private void error(Operation operation, String status, String description) {
        if (operation == null) return;
        operation.getResponses().put(status, new ApiResponse().description(description)
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));
    }
}
