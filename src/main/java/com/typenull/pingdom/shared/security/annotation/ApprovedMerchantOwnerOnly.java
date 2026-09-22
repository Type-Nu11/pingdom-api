package com.typenull.pingdom.shared.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/** 승인된 Merchant Owner 프로필 보유 여부를 확인. 사업자 검증 완료 여부는 확인 대상에서 제외. */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@merchantOwnerAuthorization.isApproved(authentication)")
public @interface ApprovedMerchantOwnerOnly {
}
