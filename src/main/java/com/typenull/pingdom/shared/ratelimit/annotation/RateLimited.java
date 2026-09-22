package com.typenull.pingdom.shared.ratelimit.annotation;

import com.typenull.pingdom.shared.ratelimit.core.RateLimitAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 컨트롤러 실행 전에 적용할 호출 제한 정책을 선택한다. 실제 식별자 추출과 차단은 RateLimitAspect가 수행한다. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    RateLimitAction value();
}
