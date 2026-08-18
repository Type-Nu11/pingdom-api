package com.typenull.pingdom.analysis.domain.exception;

import lombok.Getter;

@Getter
public class AnalysisReportException extends RuntimeException {

    private final AnalysisReportErrorCode errorCode;

    public AnalysisReportException(AnalysisReportErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
