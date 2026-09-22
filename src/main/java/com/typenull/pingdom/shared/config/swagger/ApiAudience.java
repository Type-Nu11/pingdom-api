package com.typenull.pingdom.shared.config.swagger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Locale;

/** 문서 소속만 지정하며 요청 라우팅이나 인증·인가에는 관여하지 않는다. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiAudience {
    Group value();

    enum Group {
        APP, MERCHANT, ADMIN, COMMON, CONSULTING;

        public String documentName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Group resolve(Method method) {
            ApiAudience audience = method.getAnnotation(ApiAudience.class);
            if (audience == null) {
                audience = method.getDeclaringClass().getAnnotation(ApiAudience.class);
            }
            return audience == null ? null : audience.value();
        }
    }
}
