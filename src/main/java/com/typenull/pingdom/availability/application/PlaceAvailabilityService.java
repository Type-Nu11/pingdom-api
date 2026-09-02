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

@Service
@RequiredArgsConstructor
/** 장소별 예약 가능 상품과 시간·재고 상태를 조회하고 예약 가능 여부를 판단합니다. */
public class PlaceAvailabilityService {
    private final PlaceAvailabilityRepository repository;
    private final AvailabilityAccessPolicy accessPolicy;
    private final ReservableProductRepository productRepository;
    private final Clock clock;

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
