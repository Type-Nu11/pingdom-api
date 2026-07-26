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

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-07-26T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void healthyOwnedPlaceStartsExecutionForProductDuration() {
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

    @Test
    void activeOwnedExecutionCanBeStopped() {
        VerifiedBoostExecution execution = VerifiedBoostExecution.start(selection(), 7, NOW);
        ReflectionTestUtils.setField(execution, "id", 5L);
        when(executionRepository.findOwnedByIdForUpdate(5L, 1L)).thenReturn(Optional.of(execution));

        var response = service.stop(1L, 5L);

        verify(accessPolicy).requireOwnedPlaceForUpdate(1L, 2L, NOW);
        assertThat(response.status()).isEqualTo(VerifiedBoostExecutionStatus.STOPPED);
        assertThat(response.stoppedAt()).isEqualTo(NOW);
    }

    @Test
    void repeatedStartReturnsExistingExecutionWithoutReapplyingGuardrail() {
        MerchantVerifiedBoostSelection selection = selection();
        VerifiedBoostExecution execution = VerifiedBoostExecution.start(selection, 7, NOW);
        when(selectionRepository.findByIdAndMerchantOwnerUserId(4L, 1L)).thenReturn(Optional.of(selection));
        when(accessPolicy.requireOwnedPlaceForUpdate(1L, 2L, NOW)).thenReturn(healthyOwnerPlace());
        when(executionRepository.findBySelectionId(4L)).thenReturn(Optional.of(execution));

        var response = service.start(1L, new VerifiedBoostExecutionStartRequest(4L));

        assertThat(response.status()).isEqualTo(VerifiedBoostExecutionStatus.ACTIVE);
        verifyNoInteractions(qualityGuardrail, productRepository);
    }

    @Test
    void expiredExecutionCannotBeStopped() {
        VerifiedBoostExecution execution = VerifiedBoostExecution.start(selection(), 1, NOW.minusDays(1));
        when(executionRepository.findOwnedByIdForUpdate(5L, 1L)).thenReturn(Optional.of(execution));

        assertThatThrownBy(() -> service.stop(1L, 5L))
                .isInstanceOfSatisfying(VerifiedBoostException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(VerifiedBoostErrorCode.INVALID_EXECUTION_STATE));
    }

    @Test
    void anotherOwnersExecutionIsNotExposed() {
        when(executionRepository.findOwnedByIdForUpdate(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.stop(1L, 5L))
                .isInstanceOfSatisfying(VerifiedBoostException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(VerifiedBoostErrorCode.EXECUTION_NOT_FOUND));
    }

    private MerchantVerifiedBoostSelection selection() {
        MerchantVerifiedBoostSelection selection = MerchantVerifiedBoostSelection.create(3L, 1L, 2L, "key", NOW);
        ReflectionTestUtils.setField(selection, "id", 4L);
        return selection;
    }

    private MerchantOwnerPlace healthyOwnerPlace() {
        return MerchantOwnerPlace.builder()
                .placeId(2L)
                .merchantOwnerUserId(1L)
                .operationalQualityStatus(MerchantOperationalQualityStatus.HEALTHY)
                .createdAt(NOW)
                .build();
    }
}
