package com.typenull.pingdom.identity.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MerchantOwnerErrorCode {
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Merchant Owner 프로필을 찾을 수 없습니다."),
    PROFILE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 Merchant Owner 프로필이 존재합니다."),
    INVALID_PROFILE_STATE(HttpStatus.CONFLICT, "현재 Merchant Owner 프로필 상태에서는 요청을 처리할 수 없습니다."),
    ADMIN_ACCOUNT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "관리자 계정은 Merchant Owner로 신청할 수 없습니다."),
    USER_ACCOUNT_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "탈퇴하거나 이용이 제한된 사용자는 Merchant Owner로 승인할 수 없습니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "연결할 장소를 찾을 수 없습니다."),
    OWNER_PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "Merchant Owner에게 연결된 장소를 찾을 수 없습니다."),
    PLACE_ALREADY_ASSIGNED(HttpStatus.CONFLICT, "이미 다른 Merchant Owner에게 연결된 장소가 있습니다."),
    PLACE_ALREADY_ASSIGNED_TO_REQUESTER(HttpStatus.CONFLICT, "이미 요청자에게 연결된 장소입니다."),
    PLACE_OWNERSHIP_CHANGED(HttpStatus.CONFLICT, "Claim 요청 후 장소 소유권이 변경되었습니다."),
    ACTIVE_OWNER_REQUIRED(HttpStatus.FORBIDDEN, "활성 Merchant Owner 권한이 필요합니다."),
    VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Merchant 신원 및 사업자 검증 신청을 찾을 수 없습니다."),
    VERIFICATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 Merchant 검증 신청이 존재합니다."),
    INVALID_VERIFICATION_STATE(HttpStatus.CONFLICT, "현재 Merchant 검증 상태에서는 요청을 처리할 수 없습니다."),
    VERIFICATION_REQUIRED(HttpStatus.CONFLICT, "신원 및 사업자 검증이 모두 승인되어야 합니다."),
    PLACE_CLAIM_NOT_FOUND(HttpStatus.NOT_FOUND, "상점 장소 Claim 요청을 찾을 수 없습니다."),
    PLACE_CLAIM_ALREADY_PENDING(HttpStatus.CONFLICT, "해당 장소에는 심사 대기 중인 Claim 요청이 이미 있습니다."),
    INVALID_PLACE_CLAIM_STATE(HttpStatus.CONFLICT, "현재 상점 장소 Claim 상태에서는 요청을 처리할 수 없습니다."),
    PLACE_CLAIMANT_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "활성 상태이며 검증이 완료된 Merchant Owner만 장소 Claim을 요청할 수 있습니다."),
    INVALID_ONBOARDING_METRIC(HttpStatus.BAD_REQUEST, "온보딩 완료도 값이 올바르지 않습니다."),
    INVALID_OPERATIONAL_QUALITY_METRIC(HttpStatus.BAD_REQUEST, "운영 품질 지표 값이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
