package com.typenull.pingdom.boost.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageImpl;
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
    @Mock private Clock clock;
    @InjectMocks private VerifiedBoostProductService service;

    /**
     * 상품 상태 변경 시각을 고정하고 시각을 사용하지 않는 목록 테스트에서도 공통 설정을 허용한다.
     */
    @BeforeEach
    void setUpClock() {
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-07-26T12:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    /**
     * 유효한 상품 생성 요청을 저장하면 DRAFT 상태 응답을 반환하는지 검증한다. 관리자 인증 자체는 이 단위 테스트 범위가 아니다.
     */
    @Test
    void adminCanCreateDraft() {
        var request = new VerifiedBoostProductCreateRequest("Boost", "description", 30_000L, 7);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(request);

        assertThat(response.status()).isEqualTo(VerifiedBoostProductStatus.DRAFT);
    }

    /**
     * 상품 잠금 조회가 성공하면 활성화 응답이 ACTIVE인지 검증한다.
     */
    @Test
    void adminCanActivateProduct() {
        VerifiedBoostProduct product = product();
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(product));

        var response = service.activate(3L);

        assertThat(response.status()).isEqualTo(VerifiedBoostProductStatus.ACTIVE);
    }

    /**
     * 활성화 대상의 잠금 조회가 비어 있으면 PRODUCT_NOT_FOUND로 거절하는지 검증한다.
     */
    @Test
    void unknownProductIsNotExposed() {
        when(repository.findByIdForUpdate(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(3L))
                .isInstanceOfSatisfying(VerifiedBoostException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(VerifiedBoostErrorCode.PRODUCT_NOT_FOUND));
    }

    /**
     * 상품 목록이 ACTIVE 조건 저장소 조회를 사용하고 활성 상품 1건을 반환하는지 검증한다.
     */
    @Test
    void listsOnlyActiveProducts() {
        VerifiedBoostProduct active = product();
        active.activate(NOW);
        when(repository.findAllByStatus(eq(VerifiedBoostProductStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(active)));

        var response = service.listActive(1, 20);

        assertThat(response.products()).hasSize(1);
        assertThat(response.products().getFirst().status()).isEqualTo(VerifiedBoostProductStatus.ACTIVE);
        verify(repository).findAllByStatus(eq(VerifiedBoostProductStatus.ACTIVE), any());
    }

    /**
     * 현재보다 하루 전에 생성된 가격 30,000·기간 7일의 상품 초안을 제공한다.
     */
    private VerifiedBoostProduct product() {
        return VerifiedBoostProduct.draft("Boost", "description", 30_000, 7, NOW.minusDays(1));
    }
}
