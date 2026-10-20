package com.typenull.pingdom.reservation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.identity.domain.*;
import com.typenull.pingdom.identity.domain.merchant.*;
import com.typenull.pingdom.identity.domain.repository.*;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.reservation.domain.*;
import com.typenull.pingdom.reservation.infrastructure.*;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 실제 JWT·메서드 보안·H2 저장소를 연결하며 PostgreSQL 잠금 경합은 별도 검증 대상이다. */
@Tag("integration")
@SpringBootTest(properties = {"pingdom.dev-profile.enabled=true", "pingdom.openapi.public-access.enabled=true"})
@AutoConfigureMockMvc
@Transactional
class ReservationReviewSecurityIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private Clock clock;
    @Autowired private UserRepository users;
    @Autowired private MerchantOwnerProfileRepository profiles;
    @Autowired private MerchantVerificationRepository verifications;
    @Autowired private MerchantOwnerPlaceRepository owners;
    @Autowired private MerchantPlaceMemberRepository members;
    @Autowired private MapPlaceRepository places;
    @Autowired private PlaceAvailabilityRepository slots;
    @Autowired private ReservationRepository reservations;
    @Autowired private ReservationStatusHistoryRepository histories;
    private User owner;
    private User previousOwner;
    private User admin;
    private User tourist;
    private MapPlace place;
    private PlaceAvailability slot;
    private Reservation reservation;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        owner = merchant();
        previousOwner = merchant();
        admin = user(UserRole.ADMIN);
        tourist = user(UserRole.USER);
        place = places.saveAndFlush(MapPlace.builder().name("심사 전환 테스트")
                .address("서울특별시 중구").latitude(37.56).longitude(126.97)
                .userId(previousOwner.getId()).registrant(previousOwner.getUsername()).build());
        owners.saveAndFlush(MerchantOwnerPlace.builder().placeId(place.getId())
                .merchantOwnerUserId(owner.getId()).createdAt(now).build());
        slot = PlaceAvailability.create(previousOwner.getId(), place.getId(), now.plusDays(1),
                now.plusDays(1).plusHours(1), 10, now);
        slot.reserve(2, now);
        slot = slots.saveAndFlush(slot);
        reservation = reservations.saveAndFlush(Reservation.create(tourist.getId(), slot.getId(), "request", 2,
                slot.getStartsAt(), slot.getEndsAt(), "예약자", "010-1234-5678", "창가 요청", now));
        histories.saveAndFlush(ReservationStatusHistory.of(reservation.getId(), ReservationStatus.PENDING,
                tourist.getId(), null, now));
    }

    @Test
    void ownerApprovesAndAdminCanReadRecordedResult() throws Exception {
        mvc.perform(post(path("confirm")).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.reviewedBy").value(owner.getId().intValue()));
        mvc.perform(get("/admin/reservations/" + reservation.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantOwnerUserId").value(owner.getId().intValue()))
                .andExpect(jsonPath("$.reviewedBy").value(owner.getId().intValue()))
                .andExpect(jsonPath("$.statusHistory[1].changedBy").value(owner.getId().intValue()));
        assertThat(slots.findById(slot.getId()).orElseThrow().getRemainingCapacity()).isEqualTo(8);
    }

    @Test
    void ownerRejectsAndCapacityReturnsOnce() throws Exception {
        mvc.perform(post(path("reject")).header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"영업 일정 변경\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(post(path("reject")).header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"중복\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_RESERVATION_STATE"));
        assertThat(slots.findById(slot.getId()).orElseThrow().getRemainingCapacity()).isEqualTo(10);
        assertThat(histories.findAllByReservationIdOrderByChangedAtAscIdAsc(reservation.getId())).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"confirm", "reject"})
    void touristAdminAndPreviousOwnerCannotReview(String action) throws Exception {
        for (User actor : List.of(tourist, admin, previousOwner)) {
            mvc.perform(post(path(action)).header("Authorization", bearer(actor))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"시도\"}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(reservations.findById(reservation.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(histories.findAllByReservationIdOrderByChangedAtAscIdAsc(reservation.getId())).hasSize(1);
        assertThat(slots.findById(slot.getId()).orElseThrow().getRemainingCapacity()).isEqualTo(8);
    }

    @Test
    void activeMerchantTeamMemberCannotReviewWithoutOwningPlace() throws Exception {
        User staff = merchant();
        members.saveAndFlush(MerchantPlaceMember.create(place.getId(), staff.getId(), MerchantPlaceMemberRole.STAFF,
                owner.getId(), now));
        mvc.perform(post(path("confirm")).header("Authorization", bearer(staff)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("RESERVATION_FORBIDDEN"));
    }

    @Test
    void unauthenticatedAndInactiveMerchantAreRejected() throws Exception {
        mvc.perform(post(path("confirm"))).andExpect(status().isUnauthorized());
        User inactive = user(UserRole.MERCHANT_OWNER);
        mvc.perform(post(path("confirm")).header("Authorization", bearer(inactive)))
                .andExpect(status().isForbidden());
    }

    @Test
    void previousOwnerCannotReadDetail() throws Exception {
        mvc.perform(get("/merchant-owner/reservations/" + reservation.getId())
                .header("Authorization", bearer(previousOwner)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("RESERVATION_FORBIDDEN"));
        mvc.perform(get("/merchant-owner/reservations/" + reservation.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservation.id").value(reservation.getId().intValue()))
                .andExpect(jsonPath("$.placeId").value(place.getId().intValue()))
                .andExpect(jsonPath("$.statusHistory.length()").value(1));
    }

    @Test
    void listAndAdminOwnerFilterFollowCurrentOwnershipWithOptionalDates() throws Exception {
        for (User actor : List.of(owner, previousOwner)) {
            int expected = actor == owner ? 1 : 0;
            mvc.perform(get("/merchant-owner/reservations").header("Authorization", bearer(actor))
                    .param("status", "PENDING").param("placeId", place.getId().toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(expected));
            mvc.perform(get("/admin/reservations").header("Authorization", bearer(admin))
                    .param("merchantOwnerUserId", actor.getId().toString()).param("placeId", place.getId().toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(expected));
        }
        mvc.perform(get("/merchant-owner/reservations").header("Authorization", bearer(owner))
                .param("reservationFrom", now.toString()).param("placeId", place.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/merchant-owner/reservations").header("Authorization", bearer(owner))
                .param("reservationTo", slot.getStartsAt().toString()).param("placeId", place.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void openApiExposesMerchantReviewAndAdminReadsOnly() throws Exception {
        JsonNode merchant = api("merchant");
        JsonNode adminApi = api("admin");
        JsonNode app = api("app");
        String merchantBase = "/merchant-owner/reservations";
        for (String action : List.of("confirm", "reject")) {
            String path = merchantBase + "/{reservationId}/" + action;
            assertThat(merchant.path("paths").path(path).has("post")).isTrue();
            assertThat(merchant.path("paths").path(path).at("/post/security/0/bearerAuth").isArray()).isTrue();
            assertThat(app.path("paths").has(path)).isFalse();
            assertThat(adminApi.path("paths").has("/admin/reservations/{reservationId}/" + action)).isFalse();
        }
        assertThat(merchant.path("paths").path(merchantBase).at("/get/parameters").toString())
                .contains("status", "placeId", "reservationFrom", "reservationTo", "page", "limit");
        assertThat(merchant.path("components").path("schemas").path("MerchantReservationDetailResponse")
                .path("properties").toString()).contains("reservation", "placeId", "placeName", "statusHistory");
        assertThat(merchant.path("paths").path(merchantBase + "/{reservationId}/confirm")
                .at("/post/requestBody/required").asBoolean()).isFalse();
        assertThat(merchant.path("paths").path(merchantBase + "/{reservationId}/reject")
                .at("/post/requestBody/required").asBoolean()).isTrue();
        assertThat(adminApi.path("paths").path("/admin/reservations/{reservationId}").has("get")).isTrue();
    }

    private JsonNode api(String group) throws Exception {
        return mapper.readTree(mvc.perform(get("/v3/api-docs/" + group).header("Authorization", bearer(admin))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }

    private String path(String action) {
        return "/merchant-owner/reservations/" + reservation.getId() + "/" + action;
    }

    private String bearer(User actor) {
        return "Bearer " + jwt.generateAccessToken(actor.getId(), actor.getUsername(), actor.getRole().name());
    }

    private User user(UserRole role) {
        String name = UUID.randomUUID().toString();
        return users.saveAndFlush(User.builder().username(name).email(name + "@example.com").emailVerified(true)
                .password("encoded-password").birthYear(1990).language("ko").country("KR")
                .role(role).status(UserStatus.ACTIVE).build());
    }

    private User merchant() {
        User actor = user(UserRole.MERCHANT_OWNER);
        profiles.saveAndFlush(MerchantOwnerProfile.builder().userId(actor.getId()).businessName("상점")
                .displayName("점주").contactEmail(actor.getEmail()).contactPhone("010-1234-5678")
                .status(MerchantOwnerStatus.ACTIVE).createdAt(now).updatedAt(now).build());
        verifications.saveAndFlush(MerchantVerification.builder().userId(actor.getId()).legalName("점주")
                .businessName("상점").encryptedBusinessRegistrationNumber("test-encrypted")
                .identityStatus(MerchantVerificationStatus.APPROVED).businessStatus(MerchantVerificationStatus.APPROVED)
                .createdAt(now).updatedAt(now).build());
        return actor;
    }
}
