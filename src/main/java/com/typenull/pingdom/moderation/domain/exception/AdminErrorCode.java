package com.typenull.pingdom.moderation.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AdminErrorCode {
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."),
    PLACE_DUPLICATE_NOT_FOUND(HttpStatus.NOT_FOUND, "중복 후보 장소를 찾을 수 없습니다."),
    PLACE_MERGE_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "장소 병합 요청이 올바르지 않습니다."),
    PLACE_MERGE_NOT_ALLOWED(HttpStatus.CONFLICT, "중복 장소로 확인되지 않아 병합할 수 없습니다."),
    PLACE_MERGE_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "장소 병합 이력을 찾을 수 없습니다."),
    PLACE_MERGE_ALREADY_RESTORED(HttpStatus.CONFLICT, "이미 복구된 장소 병합 이력입니다."),
    PLACE_MERGE_RESTORE_NOT_ALLOWED(HttpStatus.CONFLICT, "현재 장소 상태로는 병합 복구를 진행할 수 없습니다."),
    PLACE_KAKAO_PLACE_ID_CONFLICT(HttpStatus.CONFLICT, "이미 다른 장소에 연결된 Kakao place id입니다."),
    RECOMMENDATION_EXPLANATION_NOT_FOUND(HttpStatus.NOT_FOUND, "추천 설명 정보를 찾을 수 없습니다."),
    RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "추천 트래픽 정책 요청이 올바르지 않습니다."),
    RECOMMENDATION_TRAFFIC_POLICY_VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 추천 버전입니다."),
    RECOMMENDATION_TRAFFIC_POLICY_TOTAL_INVALID(HttpStatus.BAD_REQUEST, "추천 트래픽 비율 합계는 100이어야 합니다."),
    RECOMMENDATION_METRIC_QUERY_TOO_LARGE(HttpStatus.BAD_REQUEST, "추천 성과 조회 대상 장소가 너무 많습니다. 검색어 또는 기간 조건을 좁혀주세요."),
    AD_NOT_FOUND(HttpStatus.NOT_FOUND, "이벤트/광고를 찾을 수 없습니다."),
    AD_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "이벤트/광고 종료 시각은 시작 시각보다 이후여야 합니다."),
    UNSUPPORTED_PLACE_SORT_PARAM(HttpStatus.BAD_REQUEST, "장소 목록은 LATEST 또는 OLDEST 정렬만 지원합니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "신고 내역을 찾을 수 없습니다."),
    REPORT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 신고입니다."),
    APPEAL_NOT_FOUND(HttpStatus.NOT_FOUND, "이의제기 내역을 찾을 수 없습니다."),
    APPEAL_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 이의제기입니다."),
    USER_NOT_BANNED(HttpStatus.CONFLICT, "제재 중인 사용자가 아닙니다."),
    INVALID_SANCTION_PERIOD(HttpStatus.BAD_REQUEST, "제재 종료 시각 또는 기간이 올바르지 않습니다."),
    INVALID_SANCTION_FILTER_PERIOD(HttpStatus.BAD_REQUEST, "제재 이력 조회 종료 시각은 시작 시각보다 이후여야 합니다."),
    INVALID_AUDIT_LOG_FILTER_PERIOD(HttpStatus.BAD_REQUEST, "감사 로그 조회 종료 시각은 시작 시각보다 이후여야 합니다."),
    AUDIT_LOG_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "감사 로그 저장에 실패했습니다."),
    RECOMMENDATION_POLICY_HISTORY_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "추천 정책 변경 이력 저장에 실패했습니다."),
    POST_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "게시글 삭제에 실패했습니다."),
    S3_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "S3 설정이 누락되었습니다."),
    S3_CONNECTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S3 연결에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
