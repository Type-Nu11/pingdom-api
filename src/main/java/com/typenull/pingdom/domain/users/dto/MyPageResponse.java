package com.typenull.pingdom.domain.users.dto;

import lombok.Builder;

@Builder
public record MyPageResponse(
        Long id,
        String username,
        String name,
        String email){
}
