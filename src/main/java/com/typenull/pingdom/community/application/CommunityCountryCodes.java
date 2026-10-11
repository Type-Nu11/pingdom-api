package com.typenull.pingdom.community.application;
import java.util.*;
import com.typenull.pingdom.community.domain.exception.*;
/** ISO 국가 코드를 명시적으로 저장하며 기존 미설정 국가를 임의 추론하지 않습니다. */
public final class CommunityCountryCodes {
    private CommunityCountryCodes() {}
    public static final List<String> ALL = List.of(Locale.getISOCountries()).stream().sorted().toList();
    public static String validate(String country) {
        if (country != null && !ALL.contains(country)) throw new CommunityException(CommunityErrorCode.INVALID_COUNTRY);
        return country;
    }
}
