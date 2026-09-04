package com.typenull.pingdom.analysis.domain.exception;

import com.typenull.pingdom.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AnalysisReportErrorCode implements ErrorCode {
    MCP_SERVICE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "MCP 분석 서비스를 사용할 수 없습니다."),
    AI_SERVICE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "AI 서비스를 사용할 수 없습니다."),
    AI_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "AI 분석 응답을 처리할 수 없습니다."),
    PDF_CONVERSION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "분석 보고서 PDF 변환에 실패했습니다."),
    ANALYSIS_REPORT_FORBIDDEN(HttpStatus.FORBIDDEN, "인증된 사용자와 보고서 이메일이 일치하지 않습니다."),
    ANALYSIS_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "입지 분석 보고서를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
