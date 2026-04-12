package com.typenull.pingdom.domain.auth.dto.signup;

public record UserResponse(
        Long id,
        String username,
        String name
) {
}
