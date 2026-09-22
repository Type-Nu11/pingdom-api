package com.typenull.pingdom.availability.application;

import com.typenull.pingdom.availability.api.dto.AvailabilityResponse;
import com.typenull.pingdom.availability.api.dto.AvailabilityUpsertRequest;
import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import com.typenull.pingdom.availability.domain.AvailabilityStatus;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.availability.domain.exception.AvailabilityErrorCode;
import com.typenull.pingdom.availability.domain.exception.AvailabilityException;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.product.domain.ReservableProduct;
import com.typenull.pingdom.product.domain.ReservableProductStatus;
import com.typenull.pingdom.product.infrastructure.ReservableProductRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소의 시간별 슬롯을 상품 및 현재 점주 자격과 연결하고 예약 수량을 차감·반환.
 * 예약·반환은 슬롯을 쓰기 잠금으로 읽고 기존 호출 트랜잭션에 참여하며, 슬롯 편집은 엔티티 버전 검사를 사용.
 */
@Service
@RequiredArgsConstructor
public class PlaceAvailabilityService {
    private final PlaceAvailabilityRepository repository;
    private final AvailabilityAccessPolicy accessPolicy;
    private final ReservableProductRepository productRepository;
    private final Clock clock;

    /**
     * 현재 점주의 장소 소유권과 연결 상품의 활성 상태·유형 일치를 확인해 예약 슬롯을 저장·flush하고 상품명을 포함해 반환.
     * 상품이 없는 비일반 유형, 잘못된 기간·정원은 입력 오류로, 슬롯 고유 제약 충돌은 중복 슬롯 오류로 변환.
     */
    @Transactional
    public AvailabilityResponse create(Long ownerId, AvailabilityUpsertRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        accessPolicy.requireOwnedPlace(ownerId, request.placeId(), now);
        try {
            ReservableProduct product = requireProduct(request, null);
            requireProductReference(request, product, null);
            PlaceAvailability availability = repository.saveAndFlush(PlaceAvailability.create(
                    ownerId, request.placeId(), product == null ? null : product.getId(),
                    productType(request, product, AvailabilityProductType.GENERAL),
                    request.startsAt(), request.endsAt(), request.totalCapacity(), now));
            return AvailabilityResponse.from(availability, product == null ? null : product.getName());
        } catch (DataIntegrityViolationException exception) {
            if (hasSlotConstraint(exception)) {
                throw new AvailabilityException(AvailabilityErrorCode.AVAILABILITY_ALREADY_EXISTS);
            }
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new AvailabilityException(AvailabilityErrorCode.INVALID_AVAILABILITY_INPUT);
        }
    }

    /**
     * 기존 슬롯의 장소는 변경할 수 없으며 productId 생략은 기존 상품 참조 유지로 해석.
     * 배정된 수량이 있으면 상품·시간 변경과 배정량 미만의 총량 축소를 도메인이 거절.
     */
    @Transactional
    public AvailabilityResponse update(Long ownerId, Long id, AvailabilityUpsertRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        PlaceAvailability availability = findOwned(ownerId, id);
        if (!availability.getPlaceId().equals(request.placeId())) {
            throw new AvailabilityException(AvailabilityErrorCode.INVALID_AVAILABILITY_INPUT);
        }
        accessPolicy.requireOwnedPlace(ownerId, request.placeId(), now);
        try {
            ReservableProduct product = requireProduct(request, availability);
            requireProductReference(request, product, availability);
            boolean preservingExistingProduct = request.productId() == null && availability.getProductId() != null;
            Long productId = request.productId() == null ? availability.getProductId() : product.getId();
            AvailabilityProductType productType = productType(
                    request, product, preservingExistingProduct, availability.getProductType());
            availability.update(productId, productType, request.startsAt(), request.endsAt(),
                    request.totalCapacity(), now);
            repository.flush();
            return toResponse(availability, product);
        } catch (DataIntegrityViolationException exception) {
            if (hasSlotConstraint(exception)) {
                throw new AvailabilityException(AvailabilityErrorCode.AVAILABILITY_ALREADY_EXISTS);
            }
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new AvailabilityException(AvailabilityErrorCode.INVALID_AVAILABILITY_INPUT);
        } catch (IllegalStateException exception) {
            throw new AvailabilityException(AvailabilityErrorCode.INVALID_AVAILABILITY_STATE);
        }
    }

    /**
     * 요청 점주 명의의 슬롯과 현재 장소 소유 자격을 확인한 뒤 활성 또는 비활성 상태로 전환해 반환.
     * 대상 부재와 소유권 오류는 거절하고 도메인의 불가능한 상태 전이는 슬롯 상태 오류로 변환.
     */
    @Transactional
    public AvailabilityResponse changeStatus(Long ownerId, Long id, boolean active) {
        LocalDateTime now = LocalDateTime.now(clock);
        PlaceAvailability availability = findOwned(ownerId, id);
        accessPolicy.requireOwnedPlace(ownerId, availability.getPlaceId(), now);
        try {
            if (active) {
                availability.activate(now);
            } else {
                availability.deactivate(now);
            }
            return toResponse(availability, null);
        } catch (IllegalStateException exception) {
            throw new AvailabilityException(AvailabilityErrorCode.INVALID_AVAILABILITY_STATE);
        }
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listOwned(Long ownerId) {
        accessPolicy.requireActiveMerchantOwner(ownerId, LocalDateTime.now(clock));
        return toResponses(repository.findAllCurrentlyOwned(ownerId));
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listPublic(Long placeId) {
        return toResponses(repository.findPublicByPlaceId(
                placeId, AvailabilityStatus.ACTIVE, LocalDateTime.now(clock)));
    }

    /**
     * 현재 점주 및 상품 조건을 만족하는 슬롯을 잠그고, 연결 상품도 잠근 뒤 재고를 차감.
     * 실제 시작 시각 이전인지와 잔여 수량은 도메인이 다시 검사하므로 공개 목록에 보여도 예약은 거절될 수 있음.
     */
    @Transactional
    public PlaceAvailability reserve(Long id, int quantity) {
        LocalDateTime now = LocalDateTime.now(clock);
        PlaceAvailability availability = repository.findReservableByIdForUpdate(id, now)
                .orElseThrow(() -> new AvailabilityException(AvailabilityErrorCode.AVAILABILITY_NOT_FOUND));
        if (availability.getProductId() != null) {
            ReservableProduct product = productRepository.findByIdForUpdate(availability.getProductId())
                    .filter(candidate -> candidate.getStatus() == ReservableProductStatus.ACTIVE)
                    .orElseThrow(() -> new AvailabilityException(AvailabilityErrorCode.AVAILABILITY_NOT_FOUND));
            if (!product.getPlaceId().equals(availability.getPlaceId())) {
                throw new AvailabilityException(AvailabilityErrorCode.AVAILABILITY_NOT_FOUND);
            }
        }
        try {
            availability.reserve(quantity, now);
            return availability;
        } catch (IllegalStateException exception) {
            throw new AvailabilityException(AvailabilityErrorCode.AVAILABILITY_CAPACITY_EXCEEDED);
        }
    }

    private ReservableProduct requireProduct(AvailabilityUpsertRequest request, PlaceAvailability current) {
        Long productId = request.productId();
        if (productId == null && current != null) {
            productId = current.getProductId();
        }
        if (productId == null) return null;
        return productRepository.findByIdAndPlaceIdAndStatus(
                        productId, request.placeId(), ReservableProductStatus.ACTIVE)
                .orElseThrow(() -> new AvailabilityException(AvailabilityErrorCode.INVALID_AVAILABILITY_INPUT));
    }

    private AvailabilityProductType productType(AvailabilityUpsertRequest request, ReservableProduct product,
            AvailabilityProductType fallback) {
        return productType(request, product, false, fallback);
    }

    private AvailabilityProductType productType(AvailabilityUpsertRequest request, ReservableProduct product,
            boolean preservingExistingProduct, AvailabilityProductType fallback) {
        if (product != null) {
            if (request.productType() != null && request.productType() != product.getProductType()) {
                throw new AvailabilityException(AvailabilityErrorCode.INVALID_AVAILABILITY_INPUT);
            }
            return product.getProductType();
        }
        if (preservingExistingProduct) {
            if (request.productType() != null && request.productType() != fallback) {
                throw new AvailabilityException(AvailabilityErrorCode.INVALID_AVAILABILITY_INPUT);
            }
            return fallback;
        }
        return request.productType() == null ? fallback : request.productType();
    }

    private void requireProductReference(AvailabilityUpsertRequest request, ReservableProduct product,
            PlaceAvailability current) {
        boolean existingProductIsPreserved = current != null
                && request.productId() == null
                && current.getProductId() != null;
        AvailabilityProductType requestedType = request.productType();
        if (product == null && !existingProductIsPreserved
                && requestedType != null && requestedType != AvailabilityProductType.GENERAL) {
            throw new AvailabilityException(AvailabilityErrorCode.INVALID_AVAILABILITY_INPUT);
        }
    }

    /**
     * 예약 취소 경로에서 슬롯 행을 잠그고 요청 수량만큼 잔여 정원을 복구.
     * 슬롯 부재는 거절하며 양수가 아닌 수량이나 총 정원 초과 복구는 INVALID_AVAILABILITY_STATE로 변환.
     */
    @Transactional
    public void release(Long id, int quantity) {
        PlaceAvailability availability = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new AvailabilityException(AvailabilityErrorCode.AVAILABILITY_NOT_FOUND));
        try {
            availability.release(quantity, LocalDateTime.now(clock));
        } catch (IllegalStateException exception) {
            throw new AvailabilityException(AvailabilityErrorCode.INVALID_AVAILABILITY_STATE);
        }
    }

    private PlaceAvailability findOwned(Long ownerId, Long id) {
        return repository.findByIdAndMerchantOwnerUserId(id, ownerId)
                .orElseThrow(() -> new AvailabilityException(AvailabilityErrorCode.AVAILABILITY_NOT_FOUND));
    }

    private AvailabilityResponse toResponse(PlaceAvailability availability, ReservableProduct product) {
        if (availability.getProductId() == null) {
            return AvailabilityResponse.from(availability, null);
        }
        ReservableProduct resolvedProduct = product == null
                ? productRepository.findById(availability.getProductId()).orElse(null)
                : product;
        return AvailabilityResponse.from(availability, resolvedProduct == null ? null : resolvedProduct.getName());
    }

    private List<AvailabilityResponse> toResponses(List<PlaceAvailability> availabilities) {
        Map<Long, ReservableProduct> productsById = productRepository.findAllById(productIds(availabilities)).stream()
                .collect(Collectors.toMap(ReservableProduct::getId, Function.identity()));
        return availabilities.stream()
                .map(availability -> AvailabilityResponse.from(
                        availability,
                        availability.getProductId() == null
                                ? null
                                : productName(productsById, availability.getProductId())))
                .toList();
    }

    private Collection<Long> productIds(List<PlaceAvailability> availabilities) {
        return availabilities.stream()
                .map(PlaceAvailability::getProductId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String productName(Map<Long, ReservableProduct> productsById, Long productId) {
        ReservableProduct product = productsById.get(productId);
        if (product == null) {
            throw new AvailabilityException(AvailabilityErrorCode.INVALID_AVAILABILITY_STATE);
        }
        return product.getName();
    }

    private boolean hasConstraint(Throwable throwable, String constraintName) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasSlotConstraint(Throwable throwable) {
        return hasConstraint(throwable, "uq_place_availability_owner_slot")
                || hasConstraint(throwable, "uq_place_availability_legacy_slot")
                || hasConstraint(throwable, "uq_place_availability_product_slot");
    }
}
