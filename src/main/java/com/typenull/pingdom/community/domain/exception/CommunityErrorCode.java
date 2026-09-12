package com.typenull.pingdom.community.domain.exception;

import com.typenull.pingdom.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommunityErrorCode implements ErrorCode {
    INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "사용할 수 없는 게시글 카테고리입니다."),
    PLACE_REQUIRED(HttpStatus.BAD_REQUEST, "장소 카테고리 게시글은 연결 장소를 1개 이상 선택해야 합니다."),
    DUPLICATE_PLACE(HttpStatus.BAD_REQUEST, "같은 장소를 중복해서 연결할 수 없습니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "연결할 장소를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
