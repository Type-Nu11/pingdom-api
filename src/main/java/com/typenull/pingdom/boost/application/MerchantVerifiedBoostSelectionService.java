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

@Service
@RequiredArgsConstructor
public class MerchantVerifiedBoostSelectionService {

    private final MerchantVerifiedBoostSelectionRepository selectionRepository;
    private final VerifiedBoostProductRepository productRepository;
    private final VerifiedBoostAccessPolicy accessPolicy;
    private final Clock clock;

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
