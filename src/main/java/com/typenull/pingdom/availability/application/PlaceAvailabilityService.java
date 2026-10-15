package com.typenull.pingdom.availability.application;

import com.typenull.pingdom.availability.api.dto.AvailabilityResponse;
import com.typenull.pingdom.availability.api.dto.AvailabilityUpsertRequest;
import com.typenull.pingdom.availability.api.dto.ReservationTermsRequest;
import com.typenull.pingdom.availability.api.dto.ReservationTermsResponse;
import com.typenull.pingdom.availability.domain.AvailabilityStatus;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.availability.domain.ReservationTerms;
import com.typenull.pingdom.availability.domain.exception.AvailabilityErrorCode;
import com.typenull.pingdom.availability.domain.exception.AvailabilityException;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소의 시간별 슬롯을 현재 점주 자격과 연결하고 예약 수량을 차감·반환.
 * 예약·반환은 슬롯을 쓰기 잠금으로 읽고 기존 호출 트랜잭션에 참여하며, 슬롯 편집은 엔티티 버전 검사를 사용.
 */
@Service
@RequiredArgsConstructor
public class PlaceAvailabilityService {
    private final PlaceAvailabilityRepository repository;
    private final AvailabilityAccessPolicy accessPolicy;
    private final Clock clock;

    @Transactional
    public ReservationTermsResponse updateReservationTerms(Long ownerId, Long availabilityId,
            ReservationTermsRequest request) {
        PlaceAvailability slot = repository.findByIdForUpdate(availabilityId)
                .filter(value -> !value.isBookingRetired())
                .orElseThrow(() -> new AvailabilityException(AvailabilityErrorCode.AVAILABILITY_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        accessPolicy.requireOwnedPlace(ownerId, slot.getPlaceId(), now);
        if (!slot.getMerchantOwnerUserId().equals(ownerId)) {
            throw new AvailabilityException(AvailabilityErrorCode.PLACE_NOT_OWNED);
        }
        try {
            ReservationTerms terms = request.toTerms();
            slot.updateReservationTerms(terms, now);
            return ReservationTermsResponse.from(terms);
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new AvailabilityException(AvailabilityErrorCode.INVALID_AVAILABILITY_INPUT);
        }
    }

    /** 현재 점주의 장소 소유권과 기간·정원을 검증해 슬롯을 저장한다. */
    @Transactional
    public AvailabilityResponse create(Long ownerId, AvailabilityUpsertRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        accessPolicy.requireOwnedPlace(ownerId, request.placeId(), now);
        try {
            PlaceAvailability availability = repository.saveAndFlush(PlaceAvailability.create(
                    ownerId, request.placeId(), request.startsAt(), request.endsAt(), request.totalCapacity(), now));
            return AvailabilityResponse.from(availability);
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
     * 기존 슬롯의 장소는 변경할 수 없음.
     * 배정된 수량이 있으면 시간 변경과 배정량 미만의 총량 축소를 도메인이 거절.
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
            availability.update(request.startsAt(), request.endsAt(), request.totalCapacity(), now);
            repository.flush();
            return AvailabilityResponse.from(availability);
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
            return AvailabilityResponse.from(availability);
        } catch (IllegalStateException exception) {
            throw new AvailabilityException(AvailabilityErrorCode.INVALID_AVAILABILITY_STATE);
        }
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listOwned(Long ownerId) {
        accessPolicy.requireActiveMerchantOwner(ownerId, LocalDateTime.now(clock));
        return repository.findAllCurrentlyOwned(ownerId).stream().map(AvailabilityResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listPublic(Long placeId) {
        return repository.findPublicByPlaceId(
                placeId, AvailabilityStatus.ACTIVE, LocalDateTime.now(clock)).stream().map(AvailabilityResponse::from).toList();
    }

    /**
     * 현재 점주 조건을 만족하는 슬롯을 잠근 뒤 재고를 차감.
     * 실제 시작 시각 이전인지와 잔여 수량은 도메인이 다시 검사하므로 공개 목록에 보여도 예약은 거절될 수 있음.
     */
    @Transactional
    public PlaceAvailability reserve(Long id, int quantity) {
        LocalDateTime now = LocalDateTime.now(clock);
        PlaceAvailability availability = repository.findReservableByIdForUpdate(id, now)
                .orElseThrow(() -> new AvailabilityException(AvailabilityErrorCode.AVAILABILITY_NOT_FOUND));
        try {
            availability.reserve(quantity, now);
            return availability;
        } catch (IllegalStateException exception) {
            throw new AvailabilityException(AvailabilityErrorCode.AVAILABILITY_CAPACITY_EXCEEDED);
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
                .filter(value -> !value.isBookingRetired())
                .orElseThrow(() -> new AvailabilityException(AvailabilityErrorCode.AVAILABILITY_NOT_FOUND));
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
