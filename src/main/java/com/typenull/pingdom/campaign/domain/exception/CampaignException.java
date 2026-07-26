package com.typenull.pingdom.campaign.domain.exception;

import lombok.Getter;

@Getter
public class CampaignException extends RuntimeException {

    private final CampaignErrorCode errorCode;

    public CampaignException(CampaignErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public org.springframework.http.HttpStatus getStatus() {
        return errorCode.getStatus();
    }
}
