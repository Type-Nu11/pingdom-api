package com.typenull.pingdom.community.domain.exception;

import com.typenull.pingdom.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommunityErrorCode implements ErrorCode {
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
