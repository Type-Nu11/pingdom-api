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

    /**
     * UTC 고정 Clock과 저장소·접근 정책 mock을 사용해 시간 조건이 재현되는 서비스를 구성한다.
     */
    @BeforeEach
    void setUp() {
        service = new PlaceAvailabilityService(repository, accessPolicy, productRepository,
                Clock.fixed(Instant.parse("2026-07-20T05:00:00Z"), ZoneOffset.UTC));
    }

    /**
     * 소유 슬롯 목록 조회 시 활성 가맹점 확인과 현재 소유 목록 저장소 조회가 실행되는지 검증한다.
     * 과거 소유권만으로 목록을 제공하는 회귀를 방지한다.
     */
    @Test
    void checksOwnedListAccess() {
        when(repository.findAllCurrentlyOwned(7L)).thenReturn(List.of());

        service.listOwned(7L);

        verify(accessPolicy).requireActiveMerchantOwner(eq(7L), any(LocalDateTime.class));
        verify(repository).findAllCurrentlyOwned(7L);
    }

    /**
     * GENERAL·TICKET·CLASS 슬롯 조회에서 상품명이 null·티켓명·클래스명 순으로 반환되는지 검증한다.
     * 상품 ID 집합의 일괄 조회와 단건 조회 미호출을 확인해 슬롯별 추가 조회를 방지한다.
     */
    @Test
    void batchLoadsPublicProductNames() {
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

    /**
     * 슬롯 고유 제약 위반으로 생성 저장이 실패하면 AVAILABILITY_ALREADY_EXISTS로 변환되는지 검증한다.
     * DB 예외가 클라이언트의 중복 예약 시간 오류 계약을 우회하는 회귀를 방지한다.
     */
    @Test
    void mapsDuplicateSlotCreation() {
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

    /**
     * 기존 슬롯 수정의 flush에서 고유 제약 위반이 발생하면 AVAILABILITY_ALREADY_EXISTS로 변환되는지 검증한다.
     */
    @Test
    void mapsDuplicateSlotUpdate() {
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

    /**
     * 상품 ID와 유형을 생략한 기존 방식 수정 요청이 TICKET 유형을 유지하는지 검증한다.
     * 선택 항목 누락이 기존 상품 유형을 GENERAL로 초기화하는 회귀를 방지한다.
     */
    @Test
    void preservesOmittedProductType() {
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

    /**
     * 상품 ID를 생략하고 기존 TICKET 상품과 다른 CLASS 유형을 요청하면 INVALID_AVAILABILITY_INPUT인지 검증한다.
     * 기존 유형이 유지되고 flush가 호출되지 않는지도 확인해 불일치 상태의 저장을 방지한다.
     */
    @Test
    void rejectsPreservedProductTypeMismatch() {
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

    /**
     * 상품 정보를 생략한 수정에서도 기존 상품의 장소와 ACTIVE 상태를 재조회하는지 검증한다.
     * 상품 ID·유형 유지, 응답 상품명 및 flush 호출을 확인해 생략 필드가 상품 연결을 끊는 회귀를 방지한다.
     */
    @Test
    void revalidatesPreservedActiveProduct() {
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

    /**
     * 수정할 슬롯의 기존 상품이 활성 상품 조회에서 사라지면 INVALID_AVAILABILITY_INPUT인지 검증한다.
     * flush 미호출을 확인해 비활성 상품 연결을 그대로 저장하는 것을 방지한다.
     */
    @Test
    void rejectsInactivePreservedProduct() {
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

    /**
     * 예약 가능한 슬롯의 잠금 조회가 비어 있으면 AVAILABILITY_NOT_FOUND가 발생하는지 검증한다.
     * 소유자 조건 등을 만족하지 않는 슬롯으로 예약이 진행되는 회귀를 방지한다.
     */
    @Test
    void rejectsUnavailableReservation() {
        when(repository.findReservableByIdForUpdate(eq(9L), any(LocalDateTime.class)))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.reserve(9L, 1))
                .isInstanceOfSatisfying(AvailabilityException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(AvailabilityErrorCode.AVAILABILITY_NOT_FOUND));
    }

    /**
     * 같은 장소의 활성 TICKET 상품을 조회한 뒤 슬롯을 생성하면 해당 상품 ID·유형으로 저장되는지 검증한다.
     */
    @Test
    void createsActiveProductSlot() {
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

    /**
     * 상품 ID 없이 TICKET 슬롯을 생성하면 AvailabilityException이 발생하고 저장이 호출되지 않는지 검증한다.
     */
    @Test
    void rejectsTicketWithoutProduct() {
        AvailabilityUpsertRequest request = new AvailabilityUpsertRequest(
                3L, null, AvailabilityProductType.TICKET,
                LocalDateTime.of(2026, 7, 22, 10, 0),
                LocalDateTime.of(2026, 7, 22, 11, 0), 10);

        assertThatThrownBy(() -> service.create(7L, request))
                .isInstanceOf(AvailabilityException.class);

        verify(repository, never()).saveAndFlush(any());
    }

    /**
     * 슬롯 고유 제약 이름을 담은 Hibernate 예외를 Spring 무결성 예외로 감싼다.
     * 수정 flush 실패가 실제 중복 제약 판별 경로를 통과하도록 구성한다.
     */
    private DataIntegrityViolationException duplicateSlotViolation() {
        ConstraintViolationException constraint = new ConstraintViolationException(
                "duplicate", new SQLException(), "uq_place_availability_owner_slot");
        return new DataIntegrityViolationException("duplicate", constraint);
    }

    /**
     * 주어진 ID·상품 유형·상품명을 반환하는 상품 mock을 만들어 연결 유지와 응답 매핑을 검증한다.
     */
    private ReservableProduct product(Long id, AvailabilityProductType productType, String name) {
        ReservableProduct product = mock(ReservableProduct.class);
        when(product.getId()).thenReturn(id);
        when(product.getProductType()).thenReturn(productType);
        when(product.getName()).thenReturn(name);
        return product;
    }
}
