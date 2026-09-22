package com.typenull.pingdom.fixture.merchantteam;

/** 가맹점 팀 권한 검증에서 장소와 점주 ID의 소유 관계를 나타냄. */
public record MerchantTeamPlace(long id, long ownerId, String name) {
}
