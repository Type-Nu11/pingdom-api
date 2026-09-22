package com.typenull.pingdom.verification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Scout 신청·수정용 표시 이름과 선택 소개다. 실제 저장 시 앞뒤 공백을 정리한다. */
public record ScoutProfileRequest(
        @NotBlank @Size(max = 100) String displayName,
        @Size(max = 1000) String introduction
) {
}
