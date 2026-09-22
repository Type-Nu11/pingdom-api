package com.typenull.pingdom.identity.api.dto.merchant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * 사업자에게 연결할 전체 장소 ID 집합입니다.
 * placeIds가 null이면 빈 집합으로 정규화되어 기존 장소 연결을 모두 제거하는 요청으로 처리됩니다.
 */
public record MerchantOwnerPlaceUpdateRequest(
        @Size(max = 100) Set<@NotNull Long> placeIds,
        @Size(max = 500) String reason
) {
    public Set<Long> normalizedPlaceIds() {
        return placeIds == null ? Set.of() : Set.copyOf(placeIds);
    }
}
