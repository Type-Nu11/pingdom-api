package com.typenull.pingdom.domain.auth.dto.response;

public record LoginResponse(
        Long id,
        String username,
        String name,
        String message
) {
}
