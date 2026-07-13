package com.typenull.pingdom.shared.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        @NotEmpty(message = "허용 Origin은 하나 이상 필요합니다.")
        List<@NotBlank(message = "허용 Origin은 비어 있을 수 없습니다.") String> allowedOrigins
) {

    @AssertTrue(message = "Credential CORS에서는 와일드카드 Origin을 사용할 수 없습니다.")
    public boolean isWildcardOriginAbsent() {
        return allowedOrigins == null || allowedOrigins.stream()
                .noneMatch(origin -> origin != null && "*".equals(origin.trim()));
    }
}
