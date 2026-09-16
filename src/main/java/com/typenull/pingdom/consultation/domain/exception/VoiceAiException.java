package com.typenull.pingdom.consultation.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

public class VoiceAiException extends DomainException {
    public VoiceAiException(VoiceAiErrorCode errorCode) {
        super(errorCode);
    }

    public VoiceAiException(VoiceAiErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
