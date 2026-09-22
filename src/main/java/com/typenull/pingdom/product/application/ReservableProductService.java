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

/**
 * 현재 장소 소유 점주의 티켓·클래스 상품을 생성·조회하고 활성 상태를 변경합니다.
 * 상태 변경은 상품 행을 잠근 뒤 현재 장소 소유권을 검사하며, 기존 예약이나 슬롯 상태를 직접 변경하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class ReservableProductService {
    private final ReservableProductRepository repository;
    private final AvailabilityAccessPolicy accessPolicy;
    private final Clock clock;

    /**
     * 현재 점주의 장소 소유권을 확인해 요청 상품 유형·이름으로 예약 가능 상품을 저장하고 응답을 반환합니다.
     * 도메인 입력 조건 위반은 가용 상품 입력 오류로 변환하며 슬롯이나 예약은 생성하지 않습니다.
     */
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
