package com.typenull.pingdom.boost.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.boost.api.dto.VerifiedBoostProductPageResponse;
import com.typenull.pingdom.boost.api.dto.VerifiedBoostProductResponse;
import com.typenull.pingdom.boost.application.VerifiedBoostProductService;
import com.typenull.pingdom.boost.domain.VerifiedBoostProductStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MerchantVerifiedBoostProductControllerTest {

    @Mock private VerifiedBoostProductService service;

    private MockMvc mockMvc;

    /**
     * Boost 상품 서비스 mock에 연결한 독립 MockMvc를 만들어 컨트롤러의 응답 계약을 검증.
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MerchantVerifiedBoostProductController(service)).build();
    }

    /**
     * 기본 목록 요청이 200과 상품 ID·KRW 통화·7일 기간·ACTIVE 상태·페이지 1·다음 없음 필드를 반환하는지 검증.
     */
    @Test
    void returnsActiveProductPage() throws Exception {
        when(service.listActive(1, 20)).thenReturn(new VerifiedBoostProductPageResponse(
                List.of(new VerifiedBoostProductResponse(1L, "7일 부스트", "추천 노출을 높입니다.",
                        30_000, 7, VerifiedBoostProductStatus.ACTIVE,
                        LocalDateTime.of(2026, 8, 24, 0, 0), LocalDateTime.of(2026, 8, 24, 0, 0))),
                1, 20, 1, 1, false));

        mockMvc.perform(get("/merchant-owner/verified-boost-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].productId").value(1))
                .andExpect(jsonPath("$.products[0].currency").value("KRW"))
                .andExpect(jsonPath("$.products[0].durationDays").value(7))
                .andExpect(jsonPath("$.products[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }
}
