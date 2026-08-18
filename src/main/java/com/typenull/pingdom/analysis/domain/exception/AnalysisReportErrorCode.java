package com.typenull.pingdom.analysis.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AnalysisReportErrorCode {
    AI_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "AI 분석 응답을 처리할 수 없습니다."),
    PDF_CONVERSION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "분석 보고서 PDF 변환에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
