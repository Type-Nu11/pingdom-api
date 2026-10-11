package com.typenull.pingdom.community.domain;

import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** 기존 회원 국가의 코드·국가명 표기를 커뮤니티 필터의 안정적인 코드로 해석. */
public enum CommunityCountry {
    KR("한국", "kr", "korea", "south korea", "republic of korea", "한국", "대한민국"),
    US("미국", "us", "usa", "united states", "united states of america", "미국"),
    JP("일본", "jp", "japan", "일본"),
    VN("베트남", "vn", "vietnam", "viet nam", "베트남"),
    CN("중국", "cn", "china", "중국"),
    TH("태국", "th", "thailand", "태국");

    private final String displayName;
    private final List<String> aliases;

    CommunityCountry(String displayName, String... aliases) {
        this.displayName = displayName;
        this.aliases = List.of(aliases);
    }

    public String getDisplayName() { return displayName; }
    public List<String> getAliases() { return aliases; }

    public static CommunityCountry parseFilter(String value) {
        if (value == null) { return null; }
        return Arrays.stream(values()).filter(country -> country.name().equals(value))
                .findFirst().orElseThrow(() -> new CommunityException(CommunityErrorCode.INVALID_COUNTRY));
    }

    public static String normalize(String value) {
        if (value == null) { return null; }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(country -> country.aliases.contains(normalized))
                .map(Enum::name).findFirst().orElse(null);
    }
}
