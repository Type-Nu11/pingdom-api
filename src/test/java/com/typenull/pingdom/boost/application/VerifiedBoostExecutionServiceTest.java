package com.typenull.pingdom.boost.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.boost.api.dto.VerifiedBoostExecutionStartRequest;
import com.typenull.pingdom.boost.domain.MerchantVerifiedBoostSelection;
import com.typenull.pingdom.boost.domain.VerifiedBoostProduct;
import com.typenull.pingdom.boost.domain.VerifiedBoostExecution;
import com.typenull.pingdom.boost.domain.VerifiedBoostExecutionStatus;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostErrorCode;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostException;
import com.typenull.pingdom.boost.infrastructure.MerchantVerifiedBoostSelectionRepository;
import com.typenull.pingdom.boost.infrastructure.VerifiedBoostExecutionRepository;
import com.typenull.pingdom.boost.infrastructure.VerifiedBoostProductRepository;
import com.typenull.pingdom.identity.domain.merchant.MerchantOperationalQualityStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VerifiedBoostExecutionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 26, 12, 0);
    @Mock private VerifiedBoostExecutionRepository executionRepository;
    @Mock private MerchantVerifiedBoostSelectionRepository selectionRepository;
    @Mock private VerifiedBoostProductRepository productRepository;
    @Mock private VerifiedBoostAccessPolicy accessPolicy;
    @Mock private VerifiedBoostQualityGuardrail qualityGuardrail;
    @Mock private Clock clock;
    @InjectMocks private VerifiedBoostExecutionService service;

    /**
     * Boost 실행의 시작·중단·만료 경계를 재현하도록 UTC 현재 시각을 고정한다.
     */
    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-07-26T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    /**
     * 기존 실행이 없는 소유 장소에서 품질 정책 검사를 호출하고 상품 기간 7일 뒤를 종료 시각으로 반환하는지 검증한다.
     */
    @Test
    void startsEligibleProductExecution() {
        MerchantVerifiedBoostSelection selection = selection();
        MerchantOwnerPlace ownerPlace = healthyOwnerPlace();
        VerifiedBoostProduct product = VerifiedBoostProduct.draft("Boost", "description", 30_000, 7, NOW);
        when(selectionRepository.findByIdAndMerchantOwnerUserId(4L, 1L)).thenReturn(Optional.of(selection));
        when(accessPolicy.requireOwnedPlaceForUpdate(1L, 2L, NOW)).thenReturn(ownerPlace);
        when(executionRepository.findBySelectionId(4L)).thenReturn(Optional.empty());
        when(executionRepository.findActiveByPlaceId(2L, NOW)).thenReturn(Optional.empty());
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(executionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.start(1L, new VerifiedBoostExecutionStartRequest(4L));

        verify(qualityGuardrail).requireEligible(ownerPlace);
        assertThat(response.endsAt()).isEqualTo(NOW.plusDays(7));
    }

    /**
     * 장소에 다른 활성 실행이 있으면 EXECUTION_ALREADY_ACTIVE로 시작을 거절하는지 검증한다.
     */
    @Test
    void anotherActiveExecutionBlocksStart() {
        MerchantVerifiedBoostSelection selection = selection();
        when(selectionRepository.findByIdAndMerchantOwnerUserId(4L, 1L)).thenReturn(Optional.of(selection));
        when(accessPolicy.requireOwnedPlaceForUpdate(1L, 2L, NOW)).thenReturn(healthyOwnerPlace());
        when(executionRepository.findBySelectionId(4L)).thenReturn(Optional.empty());
        when(executionRepository.findActiveByPlaceId(2L, NOW))
                .thenReturn(Optional.of(com.typenull.pingdom.boost.domain.VerifiedBoostExecution.start(
                        selection, 7, NOW)));

        assertThatThrownBy(() -> service.start(1L, new VerifiedBoostExecutionStartRequest(4L)))
                .isInstanceOfSatisfying(VerifiedBoostException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(VerifiedBoostErrorCode.EXECUTION_ALREADY_ACTIVE));
    }

    /**
     * 소유 실행을 중단할 때 장소 소유권을 확인하고 STOPPED 상태와 현재 중단 시각을 반환하는지 검증한다.
     */
    @Test
    void stopsOwnedActiveExecution() {
        VerifiedBoostExecution execution = VerifiedBoostExecution.start(selection(), 7, NOW);
        ReflectionTestUtils.setField(execution, "id", 5L);
        when(executionRepository.findOwnedByIdForUpdate(5L, 1L)).thenReturn(Optional.of(execution));

        var response = service.stop(1L, 5L);

        verify(accessPolicy).requireOwnedPlaceForUpdate(1L, 2L, NOW);
        assertThat(response.status()).isEqualTo(VerifiedBoostExecutionStatus.STOPPED);
        assertThat(response.stoppedAt()).isEqualTo(NOW);
    }

    /**
     * 같은 선택의 실행이 이미 있으면 ACTIVE 응답을 반환하고 품질 검사와 상품 조회를 반복하지 않는지 검증한다.
     */
    @Test
    void reusesExistingBoostExecution() {
        MerchantVerifiedBoostSelection selection = selection();
        VerifiedBoostExecution execution = VerifiedBoostExecution.start(selection, 7, NOW);
        when(selectionRepository.findByIdAndMerchantOwnerUserId(4L, 1L)).thenReturn(Optional.of(selection));
        when(accessPolicy.requireOwnedPlaceForUpdate(1L, 2L, NOW)).thenReturn(healthyOwnerPlace());
        when(executionRepository.findBySelectionId(4L)).thenReturn(Optional.of(execution));

        var response = service.start(1L, new VerifiedBoostExecutionStartRequest(4L));

        assertThat(response.status()).isEqualTo(VerifiedBoostExecutionStatus.ACTIVE);
        verifyNoInteractions(qualityGuardrail, productRepository);
    }

    /**
     * 종료 시각에 도달한 실행의 중단 요청은 INVALID_EXECUTION_STATE로 변환되는지 검증한다.
     */
    @Test
    void expiredExecutionCannotBeStopped() {
        VerifiedBoostExecution execution = VerifiedBoostExecution.start(selection(), 1, NOW.minusDays(1));
        when(executionRepository.findOwnedByIdForUpdate(5L, 1L)).thenReturn(Optional.of(execution));

        assertThatThrownBy(() -> service.stop(1L, 5L))
                .isInstanceOfSatisfying(VerifiedBoostException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(VerifiedBoostErrorCode.INVALID_EXECUTION_STATE));
    }

    /**
     * 소유자 조건의 잠금 조회가 비어 있으면 EXECUTION_NOT_FOUND를 반환해 다른 점주의 실행을 노출하지 않는지 검증한다.
     */
    @Test
    void hidesUnownedExecution() {
        when(executionRepository.findOwnedByIdForUpdate(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.stop(1L, 5L))
                .isInstanceOfSatisfying(VerifiedBoostException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(VerifiedBoostErrorCode.EXECUTION_NOT_FOUND));
    }

    /**
     * 점주 1·장소 2·상품 3의 선택을 생성하고 저장된 상태를 재현할 ID 4를 설정한다.
     */
    private MerchantVerifiedBoostSelection selection() {
        MerchantVerifiedBoostSelection selection = MerchantVerifiedBoostSelection.create(3L, 1L, 2L, "key", NOW);
        ReflectionTestUtils.setField(selection, "id", 4L);
        return selection;
    }

    /**
     * 실행 조건의 품질 정책 입력으로 사용할 HEALTHY 상태의 점주 소유 장소를 만든다.
     */
    private MerchantOwnerPlace healthyOwnerPlace() {
        return MerchantOwnerPlace.builder()
                .placeId(2L)
                .merchantOwnerUserId(1L)
                .operationalQualityStatus(MerchantOperationalQualityStatus.HEALTHY)
                .createdAt(NOW)
                .build();
    }
}
