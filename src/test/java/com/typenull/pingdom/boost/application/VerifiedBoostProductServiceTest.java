package com.typenull.pingdom.boost.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.boost.api.dto.VerifiedBoostProductCreateRequest;
import com.typenull.pingdom.boost.domain.VerifiedBoostProduct;
import com.typenull.pingdom.boost.domain.VerifiedBoostProductStatus;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostErrorCode;
import com.typenull.pingdom.boost.domain.exception.VerifiedBoostException;
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
class VerifiedBoostProductServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 26, 12, 0);

    @Mock private VerifiedBoostProductRepository repository;
    @Mock private VerifiedBoostAccessPolicy accessPolicy;
    @Mock private Clock clock;
    @InjectMocks private VerifiedBoostProductService service;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-07-26T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void owningMerchantCanCreateDraft() {
        var request = new VerifiedBoostProductCreateRequest(2L, "Boost", "description", 30_000L, 7);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(1L, request);

        verify(accessPolicy).requireOwnedPlace(1L, 2L, NOW);
        assertThat(response.status()).isEqualTo(VerifiedBoostProductStatus.DRAFT);
    }

    @Test
    void owningMerchantCanActivateProduct() {
        VerifiedBoostProduct product = product();
        when(repository.findOwnedByIdForUpdate(3L, 1L)).thenReturn(Optional.of(product));

        var response = service.activate(1L, 3L);

        verify(accessPolicy).requireOwnedPlace(1L, 2L, NOW);
        assertThat(response.status()).isEqualTo(VerifiedBoostProductStatus.ACTIVE);
    }

    @Test
    void anotherOwnersProductIsNotExposed() {
        when(repository.findOwnedByIdForUpdate(3L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(1L, 3L))
                .isInstanceOfSatisfying(VerifiedBoostException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(VerifiedBoostErrorCode.PRODUCT_NOT_FOUND));
    }

    private VerifiedBoostProduct product() {
        return VerifiedBoostProduct.draft(1L, 2L, "Boost", "description", 30_000, 7, NOW.minusDays(1));
    }
}
