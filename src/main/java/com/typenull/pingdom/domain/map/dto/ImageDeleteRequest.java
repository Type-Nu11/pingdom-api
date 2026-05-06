package com.typenull.pingdom.domain.map.dto;

import jakarta.validation.constraints.NotBlank;

public record ImageDeleteRequest(
        @NotBlank(message = "삭제할 사진을 선택하세요") Long imageId) {
}
