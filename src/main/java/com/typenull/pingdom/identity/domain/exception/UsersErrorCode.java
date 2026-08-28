package com.typenull.pingdom.identity.domain.exception;

import com.typenull.pingdom.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UsersErrorCode implements ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST,"비밀번호가 서로 다릅니다."),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT,"이미 있는 아이디입니다."),
    INVALID_TRAVEL_SCHEDULE_PERIOD(HttpStatus.BAD_REQUEST, "여행 종료일은 시작일보다 빠를 수 없습니다."),
    TRAVEL_SCHEDULE_START_DATE_IN_PAST(HttpStatus.BAD_REQUEST, "여행 시작일은 오늘보다 이전일 수 없습니다."),
    TRAVEL_SCHEDULE_PERIOD_OVERLAP(HttpStatus.CONFLICT, "기존 여행 일정과 기간이 겹칩니다."),
    TRAVEL_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "여행 일정을 찾을 수 없습니다."),
    TRAVEL_SCHEDULE_NOT_EDITABLE(HttpStatus.CONFLICT, "취소된 여행 일정은 수정할 수 없습니다."),
    TRAVEL_SCHEDULE_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "여행 일정이 다른 요청으로 변경되었습니다. 다시 조회해 주세요."),
    PROFILE_IMAGE_FILE_EMPTY(HttpStatus.BAD_REQUEST, "프로필 이미지 파일은 필수입니다."),
    PROFILE_IMAGE_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "프로필 이미지는 10MB 이하여야 합니다."),
    PROFILE_IMAGE_FILE_INVALID(HttpStatus.BAD_REQUEST, "프로필 이미지는 JPEG 또는 PNG 파일이어야 합니다."),
    PROFILE_IMAGE_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "프로필 이미지 저장소를 현재 사용할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
