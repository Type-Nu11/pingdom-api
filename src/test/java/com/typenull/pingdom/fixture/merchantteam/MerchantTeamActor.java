package com.typenull.pingdom.fixture.merchantteam;

/** 가맹점 팀 시나리오에서 사용자 ID·표시 이름·역할·활성 상태를 연결하는 입력 값이다. */
public record MerchantTeamActor(long id, String username, String role, boolean active) {
}
