package com.typenull.pingdom.integration.swagger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * dev 프로필에서 생성한 app 쿠폰 및 merchant 운영 관리 문서의 계약을 검증한다.
 */
@Tag("integration")
@SpringBootTest(properties = "pingdom.dev-profile.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CouponMerchantOpenApiContractTest {

    private static final String COUPONS_PATH = "/coupons";
    private static final String COUPON_DETAIL_PATH = COUPONS_PATH + "/{couponId}";
    private static final String PLACE_INFORMATION_PATH = "/merchant-owner/places/{placeId}/information";
    private static final String OPERATING_NOTICES_PATH = "/merchant-owner/places/{placeId}/operating-notices";
    private static final String OPERATING_NOTICE_PATH = OPERATING_NOTICES_PATH + "/{noticeId}";
    private static final String OPERATING_NOTICE_CANCEL_PATH = OPERATING_NOTICE_PATH + "/cancel";
    private static final String TEAM_MEMBERS_PATH = "/merchant-owner/places/{placeId}/members";
    private static final String TEAM_INVITATIONS_PATH = TEAM_MEMBERS_PATH + "/invitations";
    private static final String TEAM_MEMBER_PATH = TEAM_MEMBERS_PATH + "/{memberId}";
    private static final String TEAM_INVITATION_ACCEPT_PATH = "/merchant-owner/invitations/{invitationId}/accept";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * app 쿠폰의 필터·nullable·오류 문서와 merchant 정보·운영 공지·팀 관리 문서의 인증 및 필수 필드를 확인한다. 실제 쿠폰 업무 요청은 실행하지 않는다.
     */
    @Test
    void couponAndMerchantContracts() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode merchantDocument = readApiDocs("/v3/api-docs/merchant");

        JsonNode couponList = operation(appDocument, COUPONS_PATH, "get");
        JsonNode couponDetail = operation(appDocument, COUPON_DETAIL_PATH, "get");
        assertThat(appDocument.path("paths").has(COUPONS_PATH)).isTrue();
        assertThat(appDocument.path("paths").has(COUPON_DETAIL_PATH)).isTrue();
        assertThat(merchantDocument.path("paths").has(COUPONS_PATH)).isFalse();
        assertThat(merchantDocument.path("paths").has(COUPON_DETAIL_PATH)).isFalse();
        assertBearerSecurity(couponList);
        assertBearerSecurity(couponDetail);
        assertThat(couponList.path("parameters").toString())
                .contains("status", "issuedFrom", "issuedTo", "page", "limit");
        assertThat(couponList.path("parameters").toString()).contains("ISSUED", "REDEEMED", "EXPIRED");
        assertErrorExample(couponList, "400", "COUPON_LIST_FILTER_INVALID");
        assertErrorExample(couponList, "401", "INVALID_TOKEN");
        assertThat(couponDetail.path("parameters").toString()).contains("couponId");
        assertThat(couponDetail.path("responses").has("200")).isTrue();
        assertThat(couponDetail.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/CouponResponse");
        assertErrorExample(couponDetail, "401", "INVALID_TOKEN");
        assertErrorExample(couponDetail, "404", "COUPON_NOT_FOUND");
        assertRequired(appDocument, "CouponResponse", List.of(
                "id", "offerId", "offerTitle", "benefitDescription", "placeId", "placeName",
                "code", "status", "issuedAt", "expiresAt", "redeemedAt"
        ));
        assertThat(appDocument.at("/components/schemas/CouponResponse/properties/redeemedAt/nullable").asBoolean())
                .isTrue();
        for (String property : List.of("offerTitle", "benefitDescription", "placeId", "placeName")) {
            assertThat(appDocument.at("/components/schemas/CouponResponse/properties/" + property + "/nullable").asBoolean())
                    .isTrue();
        }
        assertRequired(appDocument, "CouponPageResponse", List.of(
                "coupons", "page", "limit", "totalElements", "totalPages", "hasNext"
        ));

        for (String path : List.of(
                PLACE_INFORMATION_PATH,
                OPERATING_NOTICES_PATH,
                OPERATING_NOTICE_PATH,
                OPERATING_NOTICE_CANCEL_PATH,
                TEAM_MEMBERS_PATH,
                TEAM_INVITATIONS_PATH,
                TEAM_MEMBER_PATH,
                TEAM_INVITATION_ACCEPT_PATH
        )) {
            assertThat(merchantDocument.path("paths").has(path)).isTrue();
        }

        JsonNode placeInformationGet = operation(merchantDocument, PLACE_INFORMATION_PATH, "get");
        JsonNode placeInformationPut = operation(merchantDocument, PLACE_INFORMATION_PATH, "put");
        JsonNode noticeCreate = operation(merchantDocument, OPERATING_NOTICES_PATH, "post");
        JsonNode noticeUpdate = operation(merchantDocument, OPERATING_NOTICE_PATH, "patch");
        JsonNode noticeCancel = operation(merchantDocument, OPERATING_NOTICE_CANCEL_PATH, "post");
        JsonNode teamList = operation(merchantDocument, TEAM_MEMBERS_PATH, "get");
        JsonNode teamInvite = operation(merchantDocument, TEAM_INVITATIONS_PATH, "post");
        JsonNode teamRoleUpdate = operation(merchantDocument, TEAM_MEMBER_PATH, "patch");
        JsonNode teamAccept = operation(merchantDocument, TEAM_INVITATION_ACCEPT_PATH, "post");

        for (JsonNode operation : List.of(
                placeInformationGet, placeInformationPut, noticeCreate, noticeUpdate, noticeCancel,
                teamList, teamInvite, teamRoleUpdate, teamAccept
        )) {
            assertBearerSecurity(operation);
            assertThat(operation.path("responses").has("401")).isTrue();
        }
        for (String responseCode : List.of("201", "400", "401", "403", "404", "409")) {
            assertThat(noticeCreate.path("responses").has(responseCode)).isTrue();
        }
        assertErrorExample(noticeCreate, "409", "PLACE_OPERATING_NOTICE_ALREADY_ACTIVE");
        assertThat(noticeCancel.path("description").asText()).contains("PLACE_OPERATING_NOTICE_INVALID_REQUEST");

        assertThat(teamInvite.path("responses").has("409")).isTrue();
        assertThat(teamAccept.path("responses").has("410")).isTrue();
        assertErrorExample(teamAccept, "410", "MERCHANT_TEAM_INVITATION_EXPIRED");
        assertRequired(merchantDocument, "MerchantTeamInvitationResponse", List.of(
                "id", "placeId", "inviteeUserId", "role", "status", "expiresAt", "createdAt"
        ));
        assertRequired(merchantDocument, "MerchantTeamMemberResponse", List.of(
                "id", "placeId", "userId", "role", "status", "createdAt", "updatedAt"
        ));
    }

    /**
     * 문서에서 지정 경로·HTTP 메서드의 노드를 가져온다. 누락 여부는 호출한 assertion이 판단한다.
     */
    private JsonNode operation(JsonNode document, String path, String method) {
        return document.path("paths").path(path).path(method);
    }

    /**
     * operation 첫 security 항목에 bearerAuth 배열이 선언됐는지 확인한다.
     */
    private void assertBearerSecurity(JsonNode operation) {
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
    }

    /**
     * 지정 응답 상태의 오류 예시가 예시 이름과 동일한 code를 갖는지 확인한다.
     */
    private void assertErrorExample(JsonNode operation, String responseCode, String errorCode) {
        assertThat(operation.at("/responses/" + responseCode + "/content/*~1*/examples/" + errorCode + "/value/code")
                .asText()).isEqualTo(errorCode);
    }

    /**
     * 스키마 required 배열이 기대 필드 집합과 정확히 일치하는지 순서와 무관하게 확인한다.
     */
    private void assertRequired(JsonNode document, String schemaName, List<String> fields) {
        assertThat(document.path("components").path("schemas").path(schemaName).path("required"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrderElementsOf(fields);
    }

    /**
     * MockMvc로 생성된 API 문서를 조회하고 UTF-8 JSON으로 파싱한다.
     */
    private JsonNode readApiDocs(String path) throws Exception {
        String body = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }
}
