package com.typenull.pingdom.domain.users.dto;

import com.typenull.pingdom.domain.auth.domain.User;
import lombok.Builder;

@Builder
public record MyPageResponse(
        Long id,
        String username,
        String name,
        String email){

    public static MyPageResponse from(User user) {
        return MyPageResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }
}

