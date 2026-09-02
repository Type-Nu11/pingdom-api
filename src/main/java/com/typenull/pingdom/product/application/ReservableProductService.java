package com.typenull.pingdom.product.application;

import com.typenull.pingdom.availability.application.AvailabilityAccessPolicy;
import com.typenull.pingdom.availability.domain.exception.AvailabilityErrorCode;
import com.typenull.pingdom.availability.domain.exception.AvailabilityException;
import com.typenull.pingdom.product.api.dto.ReservableProductCreateRequest;
import com.typenull.pingdom.product.api.dto.ReservableProductResponse;
import com.typenull.pingdom.product.domain.ReservableProduct;
import com.typenull.pingdom.product.infrastructure.ReservableProductRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
/** Merchant가 제공하는 예약 가능 상품의 생성·수정·판매 상태를 관리합니다. */
public class ReservableProductService {
    private final ReservableProductRepository repository;
    private final AvailabilityAccessPolicy accessPolicy;
    private final Clock clock;

    @Transactional
    public ReservableProductResponse create(Long ownerId, ReservableProductCreateRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        accessPolicy.requireOwnedPlace(ownerId, request.placeId(), now);
        try {
            return ReservableProductResponse.from(repository.save(ReservableProduct.create(
                    ownerId, request.placeId(), request.productType().toAvailabilityProductType(), request.name(), now)));
        } catch (IllegalArgumentException exception) {
            throw new AvailabilityException(AvailabilityErrorCode.INVALID_AVAILABILITY_INPUT);
        }
    }

    @Transactional(readOnly = true)
    public List<ReservableProductResponse> listOwned(Long ownerId) {
        accessPolicy.requireActiveMerchantOwner(ownerId, LocalDateTime.now(clock));
        return repository.findAllCurrentlyOwned(ownerId).stream()
                .map(ReservableProductResponse::from).toList();
    }

    @Transactional
    public ReservableProductResponse changeStatus(Long ownerId, Long productId, boolean active) {
        ReservableProduct product = repository.findByIdForUpdate(productId)
                .orElseThrow(() -> new AvailabilityException(AvailabilityErrorCode.AVAILABILITY_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        accessPolicy.requireOwnedPlace(ownerId, product.getPlaceId(), now);
        product.changeStatus(active, now);
        return ReservableProductResponse.from(product);
    }
}
