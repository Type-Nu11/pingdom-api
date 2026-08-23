package com.typenull.pingdom.analysis.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

public class AnalysisReportException extends DomainException {

    public AnalysisReportException(AnalysisReportErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
