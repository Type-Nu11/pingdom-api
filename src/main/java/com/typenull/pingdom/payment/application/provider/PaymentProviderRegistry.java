package com.typenull.pingdom.payment.application.provider;

import com.typenull.pingdom.payment.domain.exception.PaymentErrorCode;
import com.typenull.pingdom.payment.domain.exception.PaymentException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 등록된 결제 사업자를 공백 제거·대문자화한 이름으로 조회합니다.
 * 미등록 이름은 도메인 예외로 거절하고, 정규화 뒤 중복되는 사업자 이름은 맵 구성 시 오류가 됩니다.
 */
@Component
public class PaymentProviderRegistry {
    private final Map<String, PaymentProvider> providers;

    public PaymentProviderRegistry(List<PaymentProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> normalize(provider.name()), Function.identity()));
    }

    public PaymentProvider require(String name) {
        PaymentProvider provider = providers.get(normalize(name));
        if (provider == null) throw new PaymentException(PaymentErrorCode.UNSUPPORTED_PROVIDER);
        return provider;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
