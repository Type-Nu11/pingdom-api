package com.typenull.pingdom.payment.application.provider;

import com.typenull.pingdom.payment.domain.exception.PaymentErrorCode;
import com.typenull.pingdom.payment.domain.exception.PaymentException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

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
