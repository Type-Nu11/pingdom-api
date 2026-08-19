package com.typenull.pingdom.shared.security.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.Parameter;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

class SecurityAnnotationTest {

    @Test
    void currentUserUsesTypedHiddenAuthenticationPrincipal() {
        AuthenticationPrincipal authenticationPrincipal =
                CurrentUser.class.getAnnotation(AuthenticationPrincipal.class);
        Parameter parameter = CurrentUser.class.getAnnotation(Parameter.class);

        assertThat(authenticationPrincipal).isNotNull();
        assertThat(authenticationPrincipal.errorOnInvalidType()).isTrue();
        assertThat(parameter).isNotNull();
        assertThat(parameter.hidden()).isTrue();
    }

    @Test
    void authorizationAnnotationsKeepCentralPolicies() {
        assertThat(expressionOf(AdminOnly.class)).isEqualTo("hasRole('ADMIN')");
        assertThat(expressionOf(AuthenticatedOnly.class)).isEqualTo("isAuthenticated()");
        assertThat(expressionOf(ActiveMerchantOwnerOnly.class))
                .isEqualTo("@merchantOwnerAuthorization.isActive(authentication)");
    }

    private String expressionOf(Class<?> annotationType) {
        return AnnotatedElementUtils.findMergedAnnotation(annotationType, PreAuthorize.class).value();
    }
}
