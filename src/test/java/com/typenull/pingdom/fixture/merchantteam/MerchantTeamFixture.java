package com.typenull.pingdom.fixture.merchantteam;

import java.util.List;

/** 가맹점 팀의 사용자·장소·HTTP 시나리오를 함께 전달하는 fixture 묶음이다. */
public record MerchantTeamFixture(List<MerchantTeamActor> actors, List<MerchantTeamPlace> places,
                                  List<MerchantTeamScenario> scenarios) {
}
