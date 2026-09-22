package com.typenull.pingdom.identity.api.dto.merchant;

import jakarta.validation.constraints.Size;

/**
 * 사업자 승인·거절·회수의 심사 사유.
 * DTO는 길이만 제한하며 거절 경로의 필수 사유 검증은 관리 서비스에서 수행.
 */
public record MerchantOwnerReviewRequest(
        @Size(max = 500) String reason
) {
}
