package com.typenull.pingdom.shared.config.swagger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Locale;

/** 문서 소속만 지정하며 요청 라우팅과 인증·인가는 적용 범위 외. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiAudience {
    Group value();

    enum Group {
        APP, MERCHANT, ADMIN, COMMON, CONSULTING;

        public String documentName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** 메서드 선언을 먼저 확인하고 없으면 선언 클래스의 소속을 사용. 둘 다 없으면 문서 그룹 미지정인 null을 반환. */
        public static Group resolve(Method method) {
            ApiAudience audience = method.getAnnotation(ApiAudience.class);
            if (audience == null) {
                audience = method.getDeclaringClass().getAnnotation(ApiAudience.class);
            }
            return audience == null ? null : audience.value();
        }
    }
}
