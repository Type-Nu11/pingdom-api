package com.typenull.pingdom.merchant.team;

import java.util.List;

public record MerchantTeamFixture(List<MerchantTeamActor> actors, List<MerchantTeamPlace> places,
                                  List<MerchantTeamScenario> scenarios) {
}
