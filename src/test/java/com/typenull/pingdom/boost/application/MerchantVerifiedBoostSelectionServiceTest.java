package com.typenull.pingdom.boost.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.boost.api.dto.VerifiedBoostSelectionCreateRequest;
import com.typenull.pingdom.boost.domain.MerchantVerifiedBoostSelection;
import com.typenull.pingdom.boost.domain.VerifiedBoostProduct;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostErrorCode;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostException;
import com.typenull.pingdom.boost.infrastructure.MerchantVerifiedBoostSelectionRepository;
import com.typenull.pingdom.boost.infrastructure.VerifiedBoostProductRepository;
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

@ExtendWith(MockitoExtension.class)
class MerchantVerifiedBoostSelectionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 26, 12, 0);
    @Mock private MerchantVerifiedBoostSelectionRepository selectionRepository;
    @Mock private VerifiedBoostProductRepository productRepository;
    @Mock private VerifiedBoostAccessPolicy accessPolicy;
    @Mock private Clock clock;
    @InjectMocks private MerchantVerifiedBoostSelectionService service;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-07-26T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void repeatedRequestReturnsExistingSelection() {
        var request = new VerifiedBoostSelectionCreateRequest(3L, 2L, "key");
        var existing = MerchantVerifiedBoostSelection.create(3L, 1L, 2L, "key", NOW);
        when(selectionRepository.findByMerchantOwnerUserIdAndPlaceIdAndIdempotencyKey(1L, 2L, "key"))
                .thenReturn(Optional.of(existing));

        var response = service.select(1L, request);

        verify(accessPolicy).requireOwnedPlaceForUpdate(1L, 2L, NOW);
        assertThat(response.productId()).isEqualTo(3L);
    }

    @Test
    void sameKeyCannotSelectAnotherProduct() {
        var request = new VerifiedBoostSelectionCreateRequest(4L, 2L, "key");
        var existing = MerchantVerifiedBoostSelection.create(3L, 1L, 2L, "key", NOW);
        when(selectionRepository.findByMerchantOwnerUserIdAndPlaceIdAndIdempotencyKey(1L, 2L, "key"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.select(1L, request))
                .isInstanceOfSatisfying(VerifiedBoostException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(VerifiedBoostErrorCode.IDEMPOTENCY_KEY_CONFLICT));
    }

    @Test
    void activeProductCanBeSelected() {
        var request = new VerifiedBoostSelectionCreateRequest(3L, 2L, "key");
        VerifiedBoostProduct product = VerifiedBoostProduct.draft("Boost", "description", 30_000, 7, NOW);
        product.activate(NOW);
        when(selectionRepository.findByMerchantOwnerUserIdAndPlaceIdAndIdempotencyKey(1L, 2L, "key"))
                .thenReturn(Optional.empty());
        when(productRepository.findActiveByIdForShare(3L)).thenReturn(Optional.of(product));
        when(selectionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.select(1L, request);

        assertThat(response.productId()).isEqualTo(3L);
    }
}
