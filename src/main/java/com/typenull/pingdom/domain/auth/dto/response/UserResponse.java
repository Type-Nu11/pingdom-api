package com.typenull.pingdom.domain.auth.dto.response;

public record UserResponse(
        Long id,
        String username,
        String name
) {
}
