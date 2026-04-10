package com.typenull.pingdom.domain.auth.dto;

public record UserResponse(
        Long id,
        String username,
        String name
) {
}
