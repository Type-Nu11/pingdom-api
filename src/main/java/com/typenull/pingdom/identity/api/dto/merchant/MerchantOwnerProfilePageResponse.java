package com.typenull.pingdom.identity.api.dto.merchant;

import java.util.List;

/**
 * 사업자 프로필 검색 결과와 1부터 시작하는 페이지 정보를 함께 반환.
 */
public record MerchantOwnerProfilePageResponse(
        List<MerchantOwnerProfileResponse> profiles,
        int page,
        int limit,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
}
