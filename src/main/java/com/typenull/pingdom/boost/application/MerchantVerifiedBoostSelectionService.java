package com.typenull.pingdom.boost.application;

import com.typenull.pingdom.boost.api.dto.VerifiedBoostSelectionCreateRequest;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostSelectionPageResponse;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostSelectionResponse;
import com.typenull.pingdom.boost.domain.MerchantVerifiedBoostSelection;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostErrorCode;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostException;
import com.typenull.pingdom.boost.infrastructure.MerchantVerifiedBoostSelectionRepository;
import com.typenull.pingdom.boost.infrastructure.VerifiedBoostProductRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 점주가 활성 부스트 상품을 장소별로 선택한 이력을 생성합니다.
 * 장소 소유 행을 잠근 뒤 점주·장소·멱등 키가 같은 선택을 재사용하며, 상품 선택 자체는 결제나 집행을 수행하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class MerchantVerifiedBoostSelectionService {

    private final MerchantVerifiedBoostSelectionRepository selectionRepository;
    private final VerifiedBoostProductRepository productRepository;
    private final VerifiedBoostAccessPolicy accessPolicy;
    private final Clock clock;

    /**
     * 공백을 제거한 멱등 키로 기존 선택을 찾고 상품 ID가 다르면 충돌로 거절합니다.
     * 신규 선택만 활성 상품을 공유 잠금으로 확인하며, 기존 선택 반환 시에는 현재 상품 활성 여부를 다시 검사하지 않습니다.
     */
    @Transactional
    public VerifiedBoostSelectionResponse select(Long ownerId, VerifiedBoostSelectionCreateRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        accessPolicy.requireOwnedPlaceForUpdate(ownerId, request.placeId(), now);
        var existing = selectionRepository.findByMerchantOwnerUserIdAndPlaceIdAndIdempotencyKey(
                ownerId, request.placeId(), request.idempotencyKey().trim());
        if (existing.isPresent()) {
            if (!existing.get().getProductId().equals(request.productId())) {
                throw new VerifiedBoostException(VerifiedBoostErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            return VerifiedBoostSelectionResponse.from(existing.get());
        }
        productRepository.findActiveByIdForShare(request.productId())
                .orElseThrow(() -> new VerifiedBoostException(VerifiedBoostErrorCode.PRODUCT_NOT_ACTIVE));
        try {
            return VerifiedBoostSelectionResponse.from(selectionRepository.save(
                    MerchantVerifiedBoostSelection.create(request.productId(), ownerId, request.placeId(),
                            request.idempotencyKey(), now)));
        } catch (IllegalArgumentException exception) {
            throw new VerifiedBoostException(VerifiedBoostErrorCode.INVALID_PRODUCT_INPUT);
        }
    }

    /**
     * 지정 점주의 부스트 선택 이력을 선택 시각·ID 내림차순의 페이지로 반환합니다.
     * 외부 페이지는 최소 1, 크기는 1~100으로 보정하며 현재 상품 활성 여부로 과거 이력을 제외하지 않습니다.
     */
    @Transactional(readOnly = true)
    public VerifiedBoostSelectionPageResponse list(Long ownerId, int page, int limit) {
        Page<MerchantVerifiedBoostSelection> result = selectionRepository.findAllByMerchantOwnerUserId(ownerId,
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100),
                        Sort.by(Sort.Order.desc("selectedAt"), Sort.Order.desc("id"))));
        return new VerifiedBoostSelectionPageResponse(
                result.getContent().stream().map(VerifiedBoostSelectionResponse::from).toList(),
                result.getNumber() + 1, result.getSize(), result.getTotalElements(), result.getTotalPages(),
                result.hasNext());
    }
}
