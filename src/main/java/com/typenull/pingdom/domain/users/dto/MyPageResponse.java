package com.typenull.pingdom.domain.users.dto;

import com.typenull.pingdom.domain.auth.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "내 정보 조회 응답")
public record MyPageResponse(
        @Schema(description = "사용자 ID", example = "1")
        Long id,
        @Schema(description = "사용자 아이디", example = "pingdom_user")
        String username,
        @Schema(description = "사용자 이름", example = "홍길동")
        String name,
        @Schema(description = "사용자 이메일", example = "pingdom@example.com")
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
