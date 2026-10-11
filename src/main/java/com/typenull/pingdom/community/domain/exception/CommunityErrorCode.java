package com.typenull.pingdom.community.domain.exception;

import com.typenull.pingdom.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommunityErrorCode implements ErrorCode {
    INVALID_COUNTRY(org.springframework.http.HttpStatus.BAD_REQUEST, "국가 코드가 올바르지 않습니다."),
    INVALID_PARENT_COMMENT(org.springframework.http.HttpStatus.BAD_REQUEST, "같은 게시글의 공개 최상위 댓글에만 답글을 작성할 수 있습니다."),
    INVALID_IMAGE(org.springframework.http.HttpStatus.BAD_REQUEST, "본인의 미사용·미만료 이미지 최대 10장만 연결할 수 있습니다."),
    IMAGE_FILE_INVALID(org.springframework.http.HttpStatus.BAD_REQUEST, "10MiB 이하의 유효한 JPEG·PNG 이미지만 업로드할 수 있습니다."),
    IMAGE_UPLOAD_UNAVAILABLE(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "이미지 저장소를 사용할 수 없습니다."),
    IMAGE_NOT_FOUND(org.springframework.http.HttpStatus.NOT_FOUND, "수정 가능한 이미지를 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "커뮤니티 신고를 찾을 수 없습니다."),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 대상입니다."),
    INVALID_REPORT(HttpStatus.BAD_REQUEST, "커뮤니티 신고 요청이 올바르지 않습니다."),
    REPORT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 커뮤니티 신고입니다."),
    INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "사용할 수 없는 게시글 카테고리입니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    PLACE_REQUIRED(HttpStatus.BAD_REQUEST, "장소 카테고리 게시글은 연결 장소를 1개 이상 선택해야 합니다."),
    DUPLICATE_PLACE(HttpStatus.BAD_REQUEST, "같은 장소를 중복해서 연결할 수 없습니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "연결할 장소를 찾을 수 없습니다."),
    PLACE_NOT_LINKED(HttpStatus.NOT_FOUND, "게시글에 연결되지 않은 장소입니다.");

    private final HttpStatus status;
    private final String message;
}
