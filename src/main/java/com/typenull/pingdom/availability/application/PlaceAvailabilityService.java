package com.typenull.pingdom.availability.application;

import com.typenull.pingdom.availability.api.dto.AvailabilityResponse;
import com.typenull.pingdom.availability.api.dto.AvailabilityUpsertRequest;
import com.typenull.pingdom.availability.domain.AvailabilityStatus;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
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

@Service
@RequiredArgsConstructor
public class PlaceAvailabilityService {
    private final PlaceAvailabilityRepository repository;
    private final AvailabilityAccessPolicy accessPolicy;
    private final Clock clock;

    @Transactional
    public AvailabilityResponse create(Long ownerId, AvailabilityUpsertRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        accessPolicy.requireOwnedPlace(ownerId, request.placeId(), now);
        try {
            return AvailabilityResponse.from(repository.saveAndFlush(PlaceAvailability.create(ownerId, request.placeId(),
                    request.startsAt(), request.endsAt(), request.totalCapacity(), now)));
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_place_availability_owner_slot")) {
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
            availability.update(request.startsAt(), request.endsAt(), request.totalCapacity(), now);
            return AvailabilityResponse.from(availability);
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
            return AvailabilityResponse.from(availability);
        } catch (IllegalStateException exception) {
            throw new AvailabilityException(AvailabilityErrorCode.INVALID_AVAILABILITY_STATE);
        }
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listOwned(Long ownerId) {
        accessPolicy.requireActiveMerchantOwner(ownerId, LocalDateTime.now(clock));
        return repository.findAllCurrentlyOwned(ownerId).stream()
                .map(AvailabilityResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listPublic(Long placeId) {
        return repository.findPublicByPlaceId(placeId, AvailabilityStatus.ACTIVE, LocalDateTime.now(clock)).stream()
                .map(AvailabilityResponse::from).toList();
    }

    @Transactional
    public void reserve(Long id, int quantity) {
        LocalDateTime now = LocalDateTime.now(clock);
        PlaceAvailability availability = repository.findReservableByIdForUpdate(id, now)
                .orElseThrow(() -> new AvailabilityException(AvailabilityErrorCode.AVAILABILITY_NOT_FOUND));
        try {
            availability.reserve(quantity, now);
        } catch (IllegalStateException exception) {
            throw new AvailabilityException(AvailabilityErrorCode.AVAILABILITY_CAPACITY_EXCEEDED);
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
}
