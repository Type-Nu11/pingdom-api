package com.typenull.pingdom.domain.auth.dto.login;

public record LoginResponse(
        Long id,
        String username,
        String name,
        String message
) {
}
