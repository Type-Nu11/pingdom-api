package com.typenull.pingdom.identity.application.port;

public record EmailSendResult(
        String providerMessageId
) {
    public static EmailSendResult sent(String providerMessageId) {
        return new EmailSendResult(providerMessageId);
    }
}
