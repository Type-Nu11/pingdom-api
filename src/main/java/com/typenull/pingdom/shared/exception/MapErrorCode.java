package com.typenull.pingdom.shared.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MapErrorCode {
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    ALREADY_REPORTED_IMAGE(HttpStatus.CONFLICT, "같은 게시글은 한 번만 신고할 수 있습니다."),
    OTHERS_NOT_DELETED(HttpStatus.FORBIDDEN,"자신의 게시글만 삭제할 수 있습니다."),
    OTHERS_NOT_UPDATE(HttpStatus.FORBIDDEN,"자신의 게시글만 수정할 수 있습니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."),
    PLACE_COORDINATE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "좌표 토큰이 유효하지 않습니다."),
    PLACE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 장소입니다."),
    PLACE_ID_REQUIRED(HttpStatus.BAD_REQUEST, "장소 ID 또는 카카오 장소 ID 중 하나는 필수입니다."),
    PLACE_SEARCH_CONDITION_INVALID(HttpStatus.BAD_REQUEST, "장소 검색 조건이 올바르지 않습니다."),
    UNSUPPORTED_PLACE_SEARCH_SORT(HttpStatus.BAD_REQUEST, "장소 검색 정렬은 LATEST 또는 NEAREST만 지원합니다."),
    FAVORITE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 즐겨찾기한 장소입니다."),
    BOOKMARK_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 북마크한 장소입니다."),
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "북마크 되어있지 않습니다."),
    OTHERS_PLACE_NOT_DELETED(HttpStatus.FORBIDDEN, "자신의 장소만 삭제할 수 있습니다."),
    DELETE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,"게시글을 삭제하는 데 실패했습니다. 잠시 후 다시 시도해 주세요."),
    S3_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR,"S3 설정이 누락되었습니다."),
    S3_CONNECTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,"S3 서버 연결에 실패했습니다."),
    UPLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "업로드 과정에서 오류가 발생하였습니다."),
    ALREADY_LIKED(HttpStatus.BAD_REQUEST,"이미 좋아요가 되어있습니다."),
    NOT_LIKED(HttpStatus.BAD_REQUEST,"좋아요가 되어있지 않습니다."),
    ALREADY_POSTED(HttpStatus.BAD_REQUEST,"한 장소엔 하나의 포스트만 가능합니다.");

    private final HttpStatus status;
    private final String message;
}
