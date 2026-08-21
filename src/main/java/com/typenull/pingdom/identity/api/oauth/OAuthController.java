package com.typenull.pingdom.identity.api.oauth;

import com.typenull.pingdom.shared.observability.LegacyApiEndpoint;
import com.typenull.pingdom.shared.observability.LegacyApiUsageMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class OAuthController {

    private final LegacyApiUsageMetrics legacyApiUsageMetrics;

    @GetMapping("/auth/google")
    public String googleLogin() {
        legacyApiUsageMetrics.record(LegacyApiEndpoint.OAUTH_GOOGLE_GET);
        return "redirect:/oauth2/authorization/google";
    }
}
