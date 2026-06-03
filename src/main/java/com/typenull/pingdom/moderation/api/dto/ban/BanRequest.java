package com.typenull.pingdom.moderation.api.dto.ban;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 밴 처리 요청 정보")
public record BanRequest(String reason) {
}
