package com.typenull.pingdom.boost.application;

import com.typenull.pingdom.boost.api.dto.VerifiedBoostProductCreateRequest;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostProductPageResponse;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostProductResponse;
import com.typenull.pingdom.boost.domain.VerifiedBoostProduct;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostErrorCode;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostException;
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
public class VerifiedBoostProductService {

    private final VerifiedBoostProductRepository repository;
    private final VerifiedBoostAccessPolicy accessPolicy;
    private final Clock clock;

    @Transactional
    public VerifiedBoostProductResponse create(Long ownerId, VerifiedBoostProductCreateRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        accessPolicy.requireOwnedPlace(ownerId, request.placeId(), now);
        try {
            return VerifiedBoostProductResponse.from(repository.save(VerifiedBoostProduct.draft(
                    ownerId, request.placeId(), request.name(), request.description(), request.priceAmount(),
                    request.durationDays(), now)));
        } catch (IllegalArgumentException exception) {
            throw new VerifiedBoostException(VerifiedBoostErrorCode.INVALID_PRODUCT_INPUT);
        }
    }

    @Transactional(readOnly = true)
    public VerifiedBoostProductPageResponse list(Long ownerId, int page, int limit) {
        Page<VerifiedBoostProduct> result = repository.findAllByMerchantOwnerUserId(ownerId,
                PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100),
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new VerifiedBoostProductPageResponse(
                result.getContent().stream().map(VerifiedBoostProductResponse::from).toList(),
                result.getNumber() + 1, result.getSize(), result.getTotalElements(), result.getTotalPages(),
                result.hasNext());
    }

    @Transactional(readOnly = true)
    public VerifiedBoostProductResponse get(Long ownerId, Long productId) {
        return VerifiedBoostProductResponse.from(repository.findByIdAndMerchantOwnerUserId(productId, ownerId)
                .orElseThrow(() -> new VerifiedBoostException(VerifiedBoostErrorCode.PRODUCT_NOT_FOUND)));
    }

    @Transactional
    public VerifiedBoostProductResponse activate(Long ownerId, Long productId) {
        LocalDateTime now = LocalDateTime.now(clock);
        VerifiedBoostProduct product = findOwnedForUpdate(ownerId, productId);
        accessPolicy.requireOwnedPlace(ownerId, product.getPlaceId(), now);
        try {
            product.activate(now);
        } catch (IllegalStateException exception) {
            throw new VerifiedBoostException(VerifiedBoostErrorCode.INVALID_PRODUCT_STATE);
        }
        return VerifiedBoostProductResponse.from(product);
    }

    @Transactional
    public VerifiedBoostProductResponse deactivate(Long ownerId, Long productId) {
        LocalDateTime now = LocalDateTime.now(clock);
        VerifiedBoostProduct product = findOwnedForUpdate(ownerId, productId);
        accessPolicy.requireOwnedPlace(ownerId, product.getPlaceId(), now);
        try {
            product.deactivate(now);
        } catch (IllegalStateException exception) {
            throw new VerifiedBoostException(VerifiedBoostErrorCode.INVALID_PRODUCT_STATE);
        }
        return VerifiedBoostProductResponse.from(product);
    }

    private VerifiedBoostProduct findOwnedForUpdate(Long ownerId, Long productId) {
        return repository.findOwnedByIdForUpdate(productId, ownerId)
                .orElseThrow(() -> new VerifiedBoostException(VerifiedBoostErrorCode.PRODUCT_NOT_FOUND));
    }
}
