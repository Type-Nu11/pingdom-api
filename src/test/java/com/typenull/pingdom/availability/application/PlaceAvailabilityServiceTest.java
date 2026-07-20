package com.typenull.pingdom.availability.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.availability.api.dto.AvailabilityUpsertRequest;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.availability.domain.exception.AvailabilityErrorCode;
import com.typenull.pingdom.availability.domain.exception.AvailabilityException;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PlaceAvailabilityServiceTest {
    private final PlaceAvailabilityRepository repository = mock(PlaceAvailabilityRepository.class);
    private final AvailabilityAccessPolicy accessPolicy = mock(AvailabilityAccessPolicy.class);
    private PlaceAvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new PlaceAvailabilityService(repository, accessPolicy,
                Clock.fixed(Instant.parse("2026-07-20T05:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void ownedListRequiresActiveMerchantAndCurrentOwnership() {
        when(repository.findAllCurrentlyOwned(7L)).thenReturn(List.of());

        service.listOwned(7L);

        verify(accessPolicy).requireActiveMerchantOwner(eq(7L), any(LocalDateTime.class));
        verify(repository).findAllCurrentlyOwned(7L);
    }

    @Test
    void duplicateSlotIsReportedAsConflict() {
        ConstraintViolationException constraint = new ConstraintViolationException(
                "duplicate", new SQLException(), "uq_place_availability_owner_slot");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate", constraint));
        AvailabilityUpsertRequest request = new AvailabilityUpsertRequest(
                3L,
                LocalDateTime.of(2026, 7, 21, 10, 0),
                LocalDateTime.of(2026, 7, 21, 11, 0),
                10
        );

        assertThatThrownBy(() -> service.create(7L, request))
                .isInstanceOfSatisfying(AvailabilityException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(AvailabilityErrorCode.AVAILABILITY_ALREADY_EXISTS));
    }

    @Test
    void duplicateSlotUpdateIsReportedAsConflict() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 5, 0);
        PlaceAvailability availability = PlaceAvailability.create(
                7L, 3L, now.plusDays(1), now.plusDays(1).plusHours(1), 10, now);
        when(repository.findByIdAndMerchantOwnerUserId(9L, 7L)).thenReturn(java.util.Optional.of(availability));
        doThrow(duplicateSlotViolation()).when(repository).flush();
        AvailabilityUpsertRequest request = new AvailabilityUpsertRequest(
                3L,
                LocalDateTime.of(2026, 7, 22, 10, 0),
                LocalDateTime.of(2026, 7, 22, 11, 0),
                10
        );

        assertThatThrownBy(() -> service.update(7L, 9L, request))
                .isInstanceOfSatisfying(AvailabilityException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(AvailabilityErrorCode.AVAILABILITY_ALREADY_EXISTS));
    }

    @Test
    void reservationIsRejectedWhenCurrentOwnerIsNotReservable() {
        when(repository.findReservableByIdForUpdate(eq(9L), any(LocalDateTime.class)))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.reserve(9L, 1))
                .isInstanceOfSatisfying(AvailabilityException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(AvailabilityErrorCode.AVAILABILITY_NOT_FOUND));
    }

    private DataIntegrityViolationException duplicateSlotViolation() {
        ConstraintViolationException constraint = new ConstraintViolationException(
                "duplicate", new SQLException(), "uq_place_availability_owner_slot");
        return new DataIntegrityViolationException("duplicate", constraint);
    }
}
