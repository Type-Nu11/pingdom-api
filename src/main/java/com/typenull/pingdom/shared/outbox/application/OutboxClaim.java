package com.typenull.pingdom.shared.outbox.application;

import java.util.Objects;

/** 선점 당시 토큰을 끝까지 전달하며 재조회한 새 토큰으로 이전 실행을 승격시키지 않는다. */
public record OutboxClaim(String eventId, String claimToken) {
    public OutboxClaim {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(claimToken, "claimToken");
    }
}
