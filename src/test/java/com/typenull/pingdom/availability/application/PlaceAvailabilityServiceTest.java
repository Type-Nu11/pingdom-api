package com.typenull.pingdom.availability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.availability.api.dto.AvailabilityResponse;
import com.typenull.pingdom.availability.api.dto.AvailabilityUpsertRequest;
import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import com.typenull.pingdom.availability.domain.AvailabilityStatus;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.availability.domain.exception.AvailabilityErrorCode;
import com.typenull.pingdom.availability.domain.exception.AvailabilityException;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.product.infrastructure.ReservableProductRepository;
import com.typenull.pingdom.product.domain.ReservableProduct;
import com.typenull.pingdom.product.domain.ReservableProductStatus;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PlaceAvailabilityServiceTest {
    private final PlaceAvailabilityRepository repository = mock(PlaceAvailabilityRepository.class);
    private final AvailabilityAccessPolicy accessPolicy = mock(AvailabilityAccessPolicy.class);
    private final ReservableProductRepository productRepository = mock(ReservableProductRepository.class);
    private PlaceAvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new PlaceAvailabilityService(repository, accessPolicy, productRepository,
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
    void publicListResolvesProductNamesWithOneBatchLookup() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 5, 0);
        PlaceAvailability general = PlaceAvailability.create(
                7L, 3L, now.plusDays(1), now.plusDays(1).plusHours(1), 10, now);
        PlaceAvailability ticket = PlaceAvailability.create(
                7L, 3L, 31L, AvailabilityProductType.TICKET,
                now.plusDays(2), now.plusDays(2).plusHours(1), 20, now);
        PlaceAvailability clazz = PlaceAvailability.create(
                7L, 3L, 32L, AvailabilityProductType.CLASS,
                now.plusDays(3), now.plusDays(3).plusHours(1), 8, now);
        ReservableProduct ticketProduct = product(31L, AvailabilityProductType.TICKET, "Museum ticket");
        ReservableProduct classProduct = product(32L, AvailabilityProductType.CLASS, "Pottery class");
        when(repository.findPublicByPlaceId(eq(3L), eq(AvailabilityStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(List.of(general, ticket, clazz));
        when(productRepository.findAllById(Set.of(31L, 32L)))
                .thenReturn(List.of(ticketProduct, classProduct));

        List<AvailabilityResponse> responses = service.listPublic(3L);

        assertThat(responses)
                .extracting(AvailabilityResponse::productName)
                .containsExactly(null, "Museum ticket", "Pottery class");
        verify(productRepository).findAllById(Set.of(31L, 32L));
        verify(productRepository, never()).findById(anyLong());
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
    void legacyUpdateWithoutProductTypePreservesCurrentType() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 5, 0);
        PlaceAvailability availability = PlaceAvailability.create(
                7L, 3L, AvailabilityProductType.TICKET,
                now.plusDays(1), now.plusDays(1).plusHours(1), 10, now);
        when(repository.findByIdAndMerchantOwnerUserId(9L, 7L)).thenReturn(java.util.Optional.of(availability));
        AvailabilityUpsertRequest request = new AvailabilityUpsertRequest(
                3L,
                null,
                null,
                LocalDateTime.of(2026, 7, 22, 10, 0),
                LocalDateTime.of(2026, 7, 22, 11, 0),
                10
        );

        service.update(7L, 9L, request);

        org.assertj.core.api.Assertions.assertThat(availability.getProductType())
                .isEqualTo(AvailabilityProductType.TICKET);
    }

    @Test
    void updateWithoutProductIdRejectsMismatchedProductType() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 5, 0);
        PlaceAvailability availability = PlaceAvailability.create(
                7L, 3L, 31L, AvailabilityProductType.TICKET,
                now.plusDays(1), now.plusDays(1).plusHours(1), 10, now);
        when(repository.findByIdAndMerchantOwnerUserId(9L, 7L)).thenReturn(java.util.Optional.of(availability));
        AvailabilityUpsertRequest request = new AvailabilityUpsertRequest(
                3L,
                null,
                AvailabilityProductType.CLASS,
                LocalDateTime.of(2026, 7, 22, 10, 0),
                LocalDateTime.of(2026, 7, 22, 11, 0),
                10
        );
        ReservableProduct product = product(31L, AvailabilityProductType.TICKET, "Museum ticket");
        when(productRepository.findByIdAndPlaceIdAndStatus(
                31L, 3L, ReservableProductStatus.ACTIVE)).thenReturn(java.util.Optional.of(product));

        assertThatThrownBy(() -> service.update(7L, 9L, request))
                .isInstanceOfSatisfying(AvailabilityException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(AvailabilityErrorCode.INVALID_AVAILABILITY_INPUT));

        org.assertj.core.api.Assertions.assertThat(availability.getProductType())
                .isEqualTo(AvailabilityProductType.TICKET);
        verify(repository, never()).flush();
    }

    @Test
    void updateWithoutProductIdRevalidatesAndPreservesActiveProduct() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 5, 0);
        PlaceAvailability availability = PlaceAvailability.create(
                7L, 3L, 31L, AvailabilityProductType.TICKET,
                now.plusDays(1), now.plusDays(1).plusHours(1), 10, now);
        ReservableProduct product = product(31L, AvailabilityProductType.TICKET, "Museum ticket");
        when(repository.findByIdAndMerchantOwnerUserId(9L, 7L)).thenReturn(java.util.Optional.of(availability));
        when(productRepository.findByIdAndPlaceIdAndStatus(
                31L, 3L, ReservableProductStatus.ACTIVE)).thenReturn(java.util.Optional.of(product));
        AvailabilityUpsertRequest request = new AvailabilityUpsertRequest(
                3L,
                null,
                null,
                LocalDateTime.of(2026, 7, 22, 10, 0),
                LocalDateTime.of(2026, 7, 22, 11, 0),
                10
        );

        AvailabilityResponse response = service.update(7L, 9L, request);

        assertThat(availability.getProductId()).isEqualTo(31L);
        assertThat(availability.getProductType()).isEqualTo(AvailabilityProductType.TICKET);
        assertThat(response.productName()).isEqualTo("Museum ticket");
        verify(productRepository).findByIdAndPlaceIdAndStatus(
                31L, 3L, ReservableProductStatus.ACTIVE);
        verify(repository).flush();
    }

    @Test
    void updateWithoutProductIdRejectsInactivePreservedProduct() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 5, 0);
        PlaceAvailability availability = PlaceAvailability.create(
                7L, 3L, 31L, AvailabilityProductType.TICKET,
                now.plusDays(1), now.plusDays(1).plusHours(1), 10, now);
        when(repository.findByIdAndMerchantOwnerUserId(9L, 7L)).thenReturn(java.util.Optional.of(availability));
        when(productRepository.findByIdAndPlaceIdAndStatus(
                31L, 3L, ReservableProductStatus.ACTIVE)).thenReturn(java.util.Optional.empty());
        AvailabilityUpsertRequest request = new AvailabilityUpsertRequest(
                3L,
                null,
                null,
                LocalDateTime.of(2026, 7, 22, 10, 0),
                LocalDateTime.of(2026, 7, 22, 11, 0),
                10
        );

        assertThatThrownBy(() -> service.update(7L, 9L, request))
                .isInstanceOfSatisfying(AvailabilityException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AvailabilityErrorCode.INVALID_AVAILABILITY_INPUT));

        verify(repository, never()).flush();
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

    @Test
    void productSlotRequiresActiveProductOwnedForSamePlace() {
        ReservableProduct product = mock(ReservableProduct.class);
        when(product.getId()).thenReturn(31L);
        when(product.getProductType()).thenReturn(AvailabilityProductType.TICKET);
        when(productRepository.findByIdAndPlaceIdAndStatus(
                31L, 3L, ReservableProductStatus.ACTIVE)).thenReturn(java.util.Optional.of(product));
        AvailabilityUpsertRequest request = new AvailabilityUpsertRequest(
                3L, 31L, AvailabilityProductType.TICKET,
                LocalDateTime.of(2026, 7, 22, 10, 0),
                LocalDateTime.of(2026, 7, 22, 11, 0), 10);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(7L, request);

        verify(repository).saveAndFlush(argThat(availability ->
                availability.getProductId().equals(31L)
                        && availability.getProductType() == AvailabilityProductType.TICKET));
    }

    @Test
    void ticketSlotWithoutProductReferenceIsRejected() {
        AvailabilityUpsertRequest request = new AvailabilityUpsertRequest(
                3L, null, AvailabilityProductType.TICKET,
                LocalDateTime.of(2026, 7, 22, 10, 0),
                LocalDateTime.of(2026, 7, 22, 11, 0), 10);

        assertThatThrownBy(() -> service.create(7L, request))
                .isInstanceOf(AvailabilityException.class);

        verify(repository, never()).saveAndFlush(any());
    }

    private DataIntegrityViolationException duplicateSlotViolation() {
        ConstraintViolationException constraint = new ConstraintViolationException(
                "duplicate", new SQLException(), "uq_place_availability_owner_slot");
        return new DataIntegrityViolationException("duplicate", constraint);
    }

    private ReservableProduct product(Long id, AvailabilityProductType productType, String name) {
        ReservableProduct product = mock(ReservableProduct.class);
        when(product.getId()).thenReturn(id);
        when(product.getProductType()).thenReturn(productType);
        when(product.getName()).thenReturn(name);
        return product;
    }
}
