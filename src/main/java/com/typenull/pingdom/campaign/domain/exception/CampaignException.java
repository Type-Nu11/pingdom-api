package com.typenull.pingdom.campaign.domain.exception;

import com.typenull.pingdom.shared.exception.DomainException;

public class CampaignException extends DomainException {

    public CampaignException(CampaignErrorCode errorCode) {
        super(errorCode);
    }
}
