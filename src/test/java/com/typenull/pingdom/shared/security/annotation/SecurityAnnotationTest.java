package com.typenull.pingdom.shared.security.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.Parameter;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

class SecurityAnnotationTest {

    /**
     * CurrentUser가 잘못된 principal 타입을 오류로 처리하고 Swagger에서 인증 인자를 숨기는 annotation을 갖는지 검증한다.
     */
    @Test
    void requiresTypedHiddenCurrentUser() {
        AuthenticationPrincipal authenticationPrincipal =
                CurrentUser.class.getAnnotation(AuthenticationPrincipal.class);
        Parameter parameter = CurrentUser.class.getAnnotation(Parameter.class);

        assertThat(authenticationPrincipal).isNotNull();
        assertThat(authenticationPrincipal.errorOnInvalidType()).isTrue();
        assertThat(parameter).isNotNull();
        assertThat(parameter.hidden()).isTrue();
    }

    /**
     * 관리자·인증 사용자·활성 점주 annotation이 중앙 PreAuthorize 표현식을 유지하는지 검증한다.
     */
    @Test
    void authorizationAnnotationsKeepCentralPolicies() {
        assertThat(expressionOf(AdminOnly.class)).isEqualTo("hasRole('ADMIN')");
        assertThat(expressionOf(AuthenticatedOnly.class)).isEqualTo("isAuthenticated()");
        assertThat(expressionOf(ActiveMerchantOwnerOnly.class))
                .isEqualTo("@merchantOwnerAuthorization.isActive(authentication)");
    }

    /**
     * 합성 annotation의 PreAuthorize를 병합 조회해 실제 보안 표현식을 읽는다.
     */
    private String expressionOf(Class<?> annotationType) {
        return AnnotatedElementUtils.findMergedAnnotation(annotationType, PreAuthorize.class).value();
    }
}
