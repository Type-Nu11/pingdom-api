package com.typenull.pingdom.campaign.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CampaignErrorCode {
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "브랜드를 찾을 수 없습니다."),
    BRAND_NAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 브랜드명입니다."),
    CAMPAIGN_NOT_FOUND(HttpStatus.NOT_FOUND, "팝업 캠페인을 찾을 수 없습니다."),
    PLACE_NOT_OWNED(HttpStatus.FORBIDDEN, "소유한 장소의 캠페인만 관리할 수 있습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "브랜드 또는 캠페인 입력값이 올바르지 않습니다."),
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "캠페인 기간이 올바르지 않습니다."),
    INVALID_STATE(HttpStatus.CONFLICT, "현재 캠페인 상태에서는 요청을 처리할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
