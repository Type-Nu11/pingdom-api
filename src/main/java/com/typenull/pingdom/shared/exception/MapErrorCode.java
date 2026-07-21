package com.typenull.pingdom.shared.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MapErrorCode {
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "신고 내역을 찾을 수 없습니다."),
    ALREADY_REPORTED_IMAGE(HttpStatus.CONFLICT, "같은 게시글은 한 번만 신고할 수 있습니다."),
    REPORTER_RESTRICTED(HttpStatus.FORBIDDEN, "허위 신고 누적으로 신고 기능이 일시 제한되었습니다."),
    REPORT_APPEAL_NOT_ALLOWED(HttpStatus.FORBIDDEN, "해당 신고에 대해 이의제기할 수 없습니다."),
    REPORT_APPEAL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 처리 대기 중인 이의제기가 있습니다."),
    OTHERS_NOT_DELETED(HttpStatus.FORBIDDEN,"자신의 게시글만 삭제할 수 있습니다."),
    OTHERS_NOT_UPDATE(HttpStatus.FORBIDDEN,"자신의 게시글만 수정할 수 있습니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."),
    PLACE_MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "장소 미디어를 찾을 수 없습니다."),
    OTHERS_PLACE_MEDIA_NOT_MANAGED(HttpStatus.FORBIDDEN, "자신의 장소 미디어만 관리할 수 있습니다."),
    PLACE_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "이벤트를 찾을 수 없습니다."),
    PLACE_EVENT_SEARCH_CONDITION_INVALID(HttpStatus.BAD_REQUEST, "이벤트 조회 기간 조건이 올바르지 않습니다."),
    PLACE_COORDINATE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "좌표 토큰이 유효하지 않습니다."),
    PLACE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 장소입니다."),
    PLACE_INFORMATION_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "장소 정보 신고를 찾을 수 없습니다."),
    PLACE_INFORMATION_REPORT_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 장소 정보 신고에 접근할 수 없습니다."),
    PLACE_INFORMATION_REPORT_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "장소 정보 신고 요청이 올바르지 않습니다."),
    PLACE_INFORMATION_REPORT_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 처리 대기 중인 장소 정보 신고가 있습니다."),
    PLACE_INFORMATION_DISPUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소 정보 반박을 찾을 수 없습니다."),
    PLACE_INFORMATION_DISPUTE_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 장소 정보 반박을 제출할 권한이 없습니다."),
    PLACE_INFORMATION_DISPUTE_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "장소 정보 반박 요청이 올바르지 않습니다."),
    PLACE_INFORMATION_REVERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "장소 정보 재확인 요청을 찾을 수 없습니다."),
    PLACE_INFORMATION_REVERIFICATION_OWNER_NOT_FOUND(HttpStatus.CONFLICT, "재확인을 요청할 장소 소유자가 없습니다."),
    PLACE_INFORMATION_REVERIFICATION_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 장소 정보 재확인 요청에 접근할 수 없습니다."),
    PLACE_INFORMATION_REVERIFICATION_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "장소 정보 재확인 요청이 올바르지 않습니다."),
    PLACE_INFORMATION_REVERIFICATION_ALREADY_ACTIVE(HttpStatus.CONFLICT, "이미 처리 중인 장소 정보 재확인 요청이 있습니다."),
    PLACE_OPERATING_NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "상점 운영 상태 공지를 찾을 수 없습니다."),
    PLACE_OPERATING_NOTICE_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 상점 운영 상태 공지를 관리할 권한이 없습니다."),
    PLACE_OPERATING_NOTICE_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "상점 운영 상태 공지 요청이 올바르지 않습니다."),
    PLACE_OPERATING_NOTICE_ALREADY_ACTIVE(HttpStatus.CONFLICT, "이미 활성 또는 예약된 상점 운영 상태 공지가 있습니다."),
    RECOMMENDATION_EXPLANATION_NOT_FOUND(HttpStatus.NOT_FOUND, "추천 설명 정보를 찾을 수 없습니다."),
    PLACE_ID_REQUIRED(HttpStatus.BAD_REQUEST, "장소 ID 또는 카카오 장소 ID 중 하나는 필수입니다."),
    PLACE_SEARCH_CONDITION_INVALID(HttpStatus.BAD_REQUEST, "장소 검색 필터 조건이 올바르지 않습니다."),
    UNSUPPORTED_PLACE_SEARCH_SORT(HttpStatus.BAD_REQUEST, "장소 검색 정렬은 LATEST, NEAREST 또는 POPULAR만 지원합니다."),
    FAVORITE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 즐겨찾기한 장소입니다."),
    BOOKMARK_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 북마크한 장소입니다."),
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "북마크 되어있지 않습니다."),
    OTHERS_PLACE_NOT_DELETED(HttpStatus.FORBIDDEN, "자신의 장소만 삭제할 수 있습니다."),
    DELETE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,"게시글을 삭제하는 데 실패했습니다. 잠시 후 다시 시도해 주세요."),
    S3_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR,"S3 설정이 누락되었습니다."),
    S3_CONNECTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,"S3 서버 연결에 실패했습니다."),
    UPLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "업로드 과정에서 오류가 발생하였습니다."),
    IMAGE_FILE_EMPTY(HttpStatus.BAD_REQUEST, "이미지 파일은 비어 있을 수 없습니다."),
    IMAGE_FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지 파일은 10MB 이하여야 합니다."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "JPEG 또는 PNG 이미지만 업로드할 수 있습니다."),
    INVALID_IMAGE_FILE(HttpStatus.BAD_REQUEST, "정상적인 이미지 파일이 아닙니다."),
    IMAGE_RESOLUTION_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지 해상도가 너무 큽니다."),
    ALREADY_LIKED(HttpStatus.BAD_REQUEST,"이미 좋아요가 되어있습니다."),
    NOT_LIKED(HttpStatus.BAD_REQUEST,"좋아요가 되어있지 않습니다."),
    ALREADY_POSTED(HttpStatus.BAD_REQUEST,"한 장소엔 하나의 포스트만 가능합니다.");

    private final HttpStatus status;
    private final String message;
}
