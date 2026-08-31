package com.typenull.pingdom.offer;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.TravelScheduleRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
import com.typenull.pingdom.identity.application.service.withdrawal.UserWithdrawalDataService;
import com.typenull.pingdom.offer.api.dto.CouponRedeemRequest;
import com.typenull.pingdom.offer.api.dto.OfferCreateRequest;
import com.typenull.pingdom.offer.api.dto.CouponResponse;
import com.typenull.pingdom.offer.application.TouristOfferService;
import com.typenull.pingdom.offer.domain.CouponEligibilityPolicy;
import com.typenull.pingdom.offer.domain.CouponExpiryPolicy;
import com.typenull.pingdom.offer.domain.CouponInventoryPolicy;
import com.typenull.pingdom.offer.domain.TouristCoupon;
import com.typenull.pingdom.offer.domain.TouristOffer;
import com.typenull.pingdom.offer.domain.OfferStatus;
import com.typenull.pingdom.offer.domain.exception.OfferErrorCode;
import com.typenull.pingdom.offer.domain.exception.OfferException;
import com.typenull.pingdom.offer.infrastructure.TouristCouponRepository;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class OfferControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private TravelScheduleRepository travelScheduleRepository;
    @Autowired private MerchantOwnerProfileRepository profileRepository;
    @Autowired private MerchantVerificationRepository verificationRepository;
    @Autowired private MerchantOwnerPlaceRepository ownerPlaceRepository;
    @Autowired private MapPlaceRepository mapPlaceRepository;
    @Autowired private TouristOfferRepository offerRepository;
    @Autowired private TouristCouponRepository couponRepository;
    @Autowired private UserWithdrawalDataService userWithdrawalDataService;
    @Autowired private TouristOfferService touristOfferService;

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void merchantCreatesOfferAndEligibleTouristIssuesAndRedeemsCoupon() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        User merchant = saveUser("offerMerchant", UserRole.MERCHANT_OWNER);
        User tourist = saveUser("offerTourist", UserRole.USER);
        MapPlace place = mapPlaceRepository.saveAndFlush(MapPlace.builder()
                .name("핑덤 카페")
                .address("서울시 중구")
                .latitude(37.5665)
                .longitude(126.9780)
                .userId(merchant.getId())
                .registrant(merchant.getUsername())
                .build());
        activateMerchant(merchant, place, now);
        travelScheduleRepository.saveAndFlush(TravelSchedule.create(
                tourist,
                LocalDate.now(ZoneOffset.UTC).minusDays(1),
                LocalDate.now(ZoneOffset.UTC).plusDays(1)
        ));

        OfferCreateRequest createRequest = new OfferCreateRequest(
                place.getId(),
                "관광객 웰컴 음료",
                "여행 중인 관광객을 위한 한정 Offer",
                "음료 1잔 무료",
                now.minusHours(1),
                now.plusDays(7),
                1,
                3
        );
        mockMvc.perform(post("/merchant-owner/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(merchant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        TouristOffer offer = offerRepository.findAll().getFirst();
        mockMvc.perform(post("/merchant-owner/offers/{offerId}/publish", offer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(merchant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(get("/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers.length()").value(1))
                .andExpect(jsonPath("$.offers[0].remainingQuantity").value(1));

        mockMvc.perform(post("/offers/{offerId}/coupons", offer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.offerTitle").value("관광객 웰컴 음료"))
                .andExpect(jsonPath("$.benefitDescription").value("음료 1잔 무료"))
                .andExpect(jsonPath("$.placeId").value(place.getId()))
                .andExpect(jsonPath("$.placeName").value("핑덤 카페"));

        mockMvc.perform(get("/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers.length()").value(0));

        mockMvc.perform(post("/merchant-owner/offers/{offerId}/close", offer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(merchant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        mockMvc.perform(get("/coupons")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons.length()").value(1))
                .andExpect(jsonPath("$.coupons[0].offerTitle").value("관광객 웰컴 음료"))
                .andExpect(jsonPath("$.coupons[0].benefitDescription").value("음료 1잔 무료"))
                .andExpect(jsonPath("$.coupons[0].placeId").value(place.getId()))
                .andExpect(jsonPath("$.coupons[0].placeName").value("핑덤 카페"));

        mockMvc.perform(post("/offers/{offerId}/coupons", offer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_ALREADY_ISSUED"));

        TouristCoupon coupon = couponRepository.findAll().getFirst();
        mockMvc.perform(post("/merchant-owner/offers/coupons/redeem")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(merchant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CouponRedeemRequest(coupon.getCode()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REDEEMED"));

        mockMvc.perform(post("/merchant-owner/offers/coupons/redeem")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(merchant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CouponRedeemRequest(coupon.getCode()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_REDEEMABLE"));
    }

    @Test
    void listsCouponsByCalculatedStatusAndIssuedAtRange() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        User tourist = saveUser("couponListTourist", UserRole.USER);

        couponRepository.saveAndFlush(TouristCoupon.issue(
                101L,
                tourist.getId(),
                "active-coupon",
                now.minusDays(2),
                now.plusDays(2)
        ));
        couponRepository.saveAndFlush(TouristCoupon.issue(
                102L,
                tourist.getId(),
                "expired-coupon",
                now.minusDays(1),
                now.minusMinutes(1)
        ));

        mockMvc.perform(get("/coupons")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist))
                        .param("status", "ISSUED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons.length()").value(1))
                .andExpect(jsonPath("$.coupons[0].code").value("active-coupon"))
                .andExpect(jsonPath("$.coupons[0].status").value("ISSUED"))
                .andExpect(jsonPath("$.coupons[0].offerTitle").value(nullValue()))
                .andExpect(jsonPath("$.coupons[0].benefitDescription").value(nullValue()))
                .andExpect(jsonPath("$.coupons[0].placeId").value(nullValue()))
                .andExpect(jsonPath("$.coupons[0].placeName").value(nullValue()));

        mockMvc.perform(get("/coupons")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist))
                        .param("status", "EXPIRED")
                        .param("issuedFrom", now.minusDays(2).minusMinutes(1).toString())
                        .param("issuedTo", now.minusHours(12).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons.length()").value(1))
                .andExpect(jsonPath("$.coupons[0].code").value("expired-coupon"))
                .andExpect(jsonPath("$.coupons[0].status").value("EXPIRED"));

        mockMvc.perform(get("/coupons")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist))
                        .param("issuedFrom", now.toString())
                        .param("issuedTo", now.minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COUPON_LIST_FILTER_INVALID"));
    }

    @Test
    void userWithoutOngoingTravelScheduleCannotIssueCoupon() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        User merchant = saveUser("ineligibleTouristMerchant", UserRole.MERCHANT_OWNER);
        User tourist = saveUser("ineligibleTourist", UserRole.USER);
        MapPlace place = mapPlaceRepository.saveAndFlush(MapPlace.builder()
                .name("자격 검증 테스트 장소")
                .address("서울시 중구")
                .latitude(37.5665)
                .longitude(126.9780)
                .userId(merchant.getId())
                .registrant(merchant.getUsername())
                .build());
        activateMerchant(merchant, place, now);
        TouristOffer offer = TouristOffer.draft(
                merchant.getId(),
                place.getId(),
                "자격 검증 Offer",
                "설명",
                "혜택",
                now.minusHours(1),
                now.plusDays(3),
                10,
                1,
                now.minusDays(1)
        );
        offer.publish(now.minusMinutes(1));
        offerRepository.saveAndFlush(offer);

        mockMvc.perform(post("/offers/{offerId}/coupons", offer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TOURIST_ELIGIBILITY_REQUIRED"));

        org.assertj.core.api.Assertions.assertThat(couponRepository.findAll()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(
                offerRepository.findById(offer.getId()).orElseThrow().getIssuedQuantity()
                ).isZero();
    }

    @Test
    void publicUnlimitedOfferCanBeViewedAndIssuedWithoutTravelSchedule() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        User merchant = saveUser("publicOfferMerchant", UserRole.MERCHANT_OWNER);
        User tourist = saveUser("publicOfferTourist", UserRole.USER);
        MapPlace place = mapPlaceRepository.saveAndFlush(MapPlace.builder()
                .name("공개 혜택 장소")
                .address("서울시 중구")
                .latitude(37.5665)
                .longitude(126.9780)
                .userId(merchant.getId())
                .registrant(merchant.getUsername())
                .build());
        activateMerchant(merchant, place, now);
        TouristOffer offer = TouristOffer.draft(
                merchant.getId(),
                place.getId(),
                "공개 무제한 Offer",
                "여행 일정 없이도 발급 가능한 혜택",
                "음료 1잔 무료",
                now.minusHours(1),
                now.plusDays(5),
                null,
                3,
                CouponEligibilityPolicy.PUBLIC,
                CouponInventoryPolicy.UNLIMITED,
                CouponExpiryPolicy.OFFER_END,
                now.minusDays(1)
        );
        offer.publish(now.minusMinutes(1));
        offerRepository.saveAndFlush(offer);

        mockMvc.perform(get("/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers.length()").value(1))
                .andExpect(jsonPath("$.offers[0].totalQuantity").doesNotExist())
                .andExpect(jsonPath("$.offers[0].remainingQuantity").doesNotExist())
                .andExpect(jsonPath("$.offers[0].eligibilityPolicy").value("PUBLIC"))
                .andExpect(jsonPath("$.offers[0].inventoryPolicy").value("UNLIMITED"))
                .andExpect(jsonPath("$.offers[0].expiryPolicy").value("OFFER_END"));

        mockMvc.perform(post("/offers/{offerId}/coupons", offer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").value(offer.getEndsAt().toString()));
    }

    @Test
    void offerEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/offers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/coupons")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/merchant-owner/offers")).andExpect(status().isUnauthorized());
    }

    @Test
    void merchantWithdrawalClosesOffersWithoutDeletingTouristCoupons() {
        WithdrawalFixture fixture = withdrawalFixture();

        userWithdrawalDataService.cleanupUserOwnedData(fixture.merchant().getId());

        org.assertj.core.api.Assertions.assertThat(
                offerRepository.findById(fixture.offer().getId()).orElseThrow().getStatus()
        ).isEqualTo(OfferStatus.CLOSED);
        org.assertj.core.api.Assertions.assertThat(couponRepository.findAll()).hasSize(1);
    }

    @Test
    void touristWithdrawalDeletesOwnCouponsWithoutClosingMerchantOffer() {
        WithdrawalFixture fixture = withdrawalFixture();

        userWithdrawalDataService.cleanupUserOwnedData(fixture.tourist().getId());

        org.assertj.core.api.Assertions.assertThat(
                offerRepository.findById(fixture.offer().getId()).orElseThrow().getStatus()
        ).isEqualTo(OfferStatus.PUBLISHED);
        org.assertj.core.api.Assertions.assertThat(couponRepository.findAll()).isEmpty();
    }

    private WithdrawalFixture withdrawalFixture() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        User merchant = saveUser("withdrawOfferMerchant", UserRole.MERCHANT_OWNER);
        User tourist = saveUser("withdrawOfferTourist", UserRole.USER);
        MapPlace place = mapPlaceRepository.saveAndFlush(MapPlace.builder()
                .name("탈퇴 테스트 장소")
                .address("서울시 중구")
                .latitude(37.5665)
                .longitude(126.9780)
                .userId(merchant.getId())
                .registrant(merchant.getUsername())
                .build());
        activateMerchant(merchant, place, now);
        TouristOffer offer = TouristOffer.draft(
                merchant.getId(),
                place.getId(),
                "탈퇴 테스트 Offer",
                "설명",
                "혜택",
                now.minusHours(1),
                now.plusDays(3),
                10,
                1,
                now.minusDays(1)
        );
        offer.publish(now.minusMinutes(1));
        offerRepository.saveAndFlush(offer);
        couponRepository.saveAndFlush(TouristCoupon.issue(
                offer.getId(),
                tourist.getId(),
                "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                now,
                now.plusDays(1)
        ));
        return new WithdrawalFixture(merchant, tourist, offer);
    }

    @Test
    void concurrentCouponIssueDoesNotExceedOfferQuantityForDifferentUsers() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        User merchant = saveUser("concurrentOfferMerchant", UserRole.MERCHANT_OWNER);
        User firstTourist = saveUser("firstConcurrentOfferTourist", UserRole.USER);
        User secondTourist = saveUser("secondConcurrentOfferTourist", UserRole.USER);
        MapPlace place = mapPlaceRepository.saveAndFlush(MapPlace.builder()
                .name("동시 발급 테스트 장소")
                .address("서울시 중구")
                .latitude(37.5665)
                .longitude(126.9780)
                .userId(merchant.getId())
                .registrant(merchant.getUsername())
                .build());
        activateMerchant(merchant, place, now);
        travelScheduleRepository.saveAndFlush(TravelSchedule.create(
                firstTourist,
                LocalDate.now(ZoneOffset.UTC).minusDays(1),
                LocalDate.now(ZoneOffset.UTC).plusDays(1)
        ));
        travelScheduleRepository.saveAndFlush(TravelSchedule.create(
                secondTourist,
                LocalDate.now(ZoneOffset.UTC).minusDays(1),
                LocalDate.now(ZoneOffset.UTC).plusDays(1)
        ));
        TouristOffer offer = TouristOffer.draft(
                merchant.getId(),
                place.getId(),
                "동시 발급 Offer",
                "설명",
                "혜택",
                now.minusHours(1),
                now.plusDays(3),
                1,
                1,
                now.minusDays(1)
        );
        offer.publish(now.minusMinutes(1));
        offerRepository.saveAndFlush(offer);

        CyclicBarrier lockRaceBarrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = List.of(
                    executor.submit(() -> issueAfterBarrier(
                            lockRaceBarrier,
                            firstTourist.getId(),
                            offer.getId()
                    )),
                    executor.submit(() -> issueAfterBarrier(
                            lockRaceBarrier,
                            secondTourist.getId(),
                            offer.getId()
                    ))
            );
            List<Object> results = List.of(
                    futures.get(0).get(10, TimeUnit.SECONDS),
                    futures.get(1).get(10, TimeUnit.SECONDS)
            );

            org.assertj.core.api.Assertions.assertThat(results)
                    .filteredOn(CouponResponse.class::isInstance)
                    .hasSize(1);
            org.assertj.core.api.Assertions.assertThat(results)
                    .filteredOn(result -> result == OfferErrorCode.OFFER_SOLD_OUT)
                    .hasSize(1);
            org.assertj.core.api.Assertions.assertThat(couponRepository.findAll()).hasSize(1);
            org.assertj.core.api.Assertions.assertThat(
                    offerRepository.findById(offer.getId()).orElseThrow().getIssuedQuantity()
            ).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Object issueAfterBarrier(CyclicBarrier barrier, Long userId, Long offerId) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            return touristOfferService.issue(userId, offerId);
        } catch (OfferException exception) {
            return exception.getErrorCode();
        }
    }

    private record WithdrawalFixture(User merchant, User tourist, TouristOffer offer) {
    }

    private User saveUser(String username, UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(role)
                .build());
    }

    private void activateMerchant(User merchant, MapPlace place, LocalDateTime now) {
        MerchantOwnerProfile profile = MerchantOwnerProfile.pending(
                merchant.getId(),
                "핑덤 카페",
                "핑덤 사장님",
                null,
                "owner@example.com",
                "010-1111-2222",
                now.minusDays(2)
        );
        profile.approve(999L, now.minusDays(1));
        profileRepository.saveAndFlush(profile);

        MerchantVerification verification = MerchantVerification.pending(
                merchant.getId(),
                "김핑덤",
                "핑덤 카페",
                "encrypted-number",
                now.minusDays(2)
        );
        verification.review(999L, true, true, "확인 완료", now.minusDays(1));
        verificationRepository.saveAndFlush(verification);
        ownerPlaceRepository.saveAndFlush(MerchantOwnerPlace.builder()
                .placeId(place.getId())
                .merchantOwnerUserId(merchant.getId())
                .createdAt(now.minusDays(1))
                .build());
    }

    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUsername(),
                user.getRole().name()
        );
    }

    private void cleanup() {
        couponRepository.deleteAllInBatch();
        offerRepository.deleteAllInBatch();
        travelScheduleRepository.deleteAllInBatch();
        ownerPlaceRepository.deleteAllInBatch();
        verificationRepository.deleteAllInBatch();
        profileRepository.deleteAllInBatch();
        mapPlaceRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }
}
