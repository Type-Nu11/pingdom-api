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

    /**
     * 상품 선택의 소유권 확인 시각을 고정하도록 Clock의 현재 시각과 UTC 시간대를 설정.
     */
    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-07-26T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    /**
     * 같은 점주·장소·멱등 키의 선택이 있으면 소유 장소 접근을 재확인하고 기존 상품 ID를 반환하는지 검증.
     */
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

    /**
     * 같은 멱등 키로 다른 상품을 선택하면 IDEMPOTENCY_KEY_CONFLICT가 발생하는지 검증.
     */
    @Test
    void rejectsConflictingSelectionKey() {
        var request = new VerifiedBoostSelectionCreateRequest(4L, 2L, "key");
        var existing = MerchantVerifiedBoostSelection.create(3L, 1L, 2L, "key", NOW);
        when(selectionRepository.findByMerchantOwnerUserIdAndPlaceIdAndIdempotencyKey(1L, 2L, "key"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.select(1L, request))
                .isInstanceOfSatisfying(VerifiedBoostException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(VerifiedBoostErrorCode.IDEMPOTENCY_KEY_CONFLICT));
    }

    /**
     * 기존 선택이 없고 활성 상품 잠금 조회가 성공하면 선택 결과에 요청 상품 ID가 반영되는지 검증.
     */
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
