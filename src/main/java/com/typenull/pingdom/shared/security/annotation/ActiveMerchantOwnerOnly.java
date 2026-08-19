package com.typenull.pingdom.shared.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/** 활성화된 Merchant Owner만 메서드를 호출할 수 있다. */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@merchantOwnerAuthorization.isActive(authentication)")
public @interface ActiveMerchantOwnerOnly {
}
