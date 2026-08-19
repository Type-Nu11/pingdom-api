package com.typenull.pingdom.fixture.merchantteam;

import java.util.List;

public final class MerchantTeamFixtures {
    private MerchantTeamFixtures() {
    }

    public static MerchantTeamFixture realistic() {
        return new MerchantTeamFixture(
                List.of(new MerchantTeamActor(101L, "merchant-owner", "OWNER", true),
                        new MerchantTeamActor(102L, "merchant-editor", "EDITOR", true),
                        new MerchantTeamActor(103L, "merchant-viewer", "VIEWER", true),
                        new MerchantTeamActor(104L, "merchant-outsider", "USER", true),
                        new MerchantTeamActor(105L, "withdrawn-member", "EDITOR", false)),
                List.of(new MerchantTeamPlace(201L, 101L, "성수 팝업 스토어")),
                List.of(new MerchantTeamScenario("list-members-normal", MerchantTeamScenarioType.NORMAL, "GET",
                                "/merchant-owner/places/201/members", 200, null,
                                List.of("owner 포함", "role 반환", "비활성 사용자 제외")),
                        new MerchantTeamScenario("invite-member-normal", MerchantTeamScenarioType.NORMAL, "POST",
                                "/merchant-owner/places/201/members/invitations", 201, null,
                                List.of("초대 생성", "초대 상태 PENDING", "중복 초대 방지")),
                        new MerchantTeamScenario("invite-member-duplicate-boundary", MerchantTeamScenarioType.BOUNDARY, "POST",
                                "/merchant-owner/places/201/members/invitations", 409,
                                "MERCHANT_TEAM_INVITATION_ALREADY_EXISTS", List.of("동일 장소·사용자 중복", "기존 초대 유지")),
                        new MerchantTeamScenario("viewer-cannot-invite", MerchantTeamScenarioType.AUTHORIZATION, "POST",
                                "/merchant-owner/places/201/members/invitations", 403,
                                "MERCHANT_TEAM_PERMISSION_REQUIRED", List.of("EDITOR 초대 금지", "초대 미생성")),
                        new MerchantTeamScenario("outsider-cannot-list-members", MerchantTeamScenarioType.AUTHORIZATION, "GET",
                                "/merchant-owner/places/201/members", 403,
                                "MERCHANT_TEAM_PERMISSION_REQUIRED", List.of("타 장소 사용자 차단", "멤버 목록 비공개")),
                        new MerchantTeamScenario("withdrawn-member-role-update-fails", MerchantTeamScenarioType.FAILURE, "PATCH",
                                "/merchant-owner/places/201/members/105", 409,
                                "MERCHANT_TEAM_MEMBER_NOT_ACTIVE", List.of("탈퇴 사용자 검증", "권한 변경 미반영"))));
    }
}
