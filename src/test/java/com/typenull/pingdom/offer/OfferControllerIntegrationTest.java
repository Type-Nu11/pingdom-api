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

    /**
     * 각 Offer 통합 테스트 전에 이전 쿠폰·Offer·여행·점주·장소·사용자 데이터를 정리한다.
     */
    @BeforeEach
    void setUp() {
        cleanup();
    }

    /**
     * 테스트 결과와 무관하게 생성 데이터를 정리해 다음 Spring 통합 테스트에 상태가 누적되지 않게 한다.
     */
    @AfterEach
    void tearDown() {
        cleanup();
    }

    /**
     * 점주가 초안 생성·게시하고 여행 중 관광객이 한정 쿠폰을 발급받은 뒤 점주가 사용하는 API 흐름을 검증한다.
     * 품절 목록 제외·Offer 종료 후 쿠폰 부가 정보 유지·중복 발급 409·재사용 409를 함께 확인한다.
     */
    @Test
    void completesOfferCouponLifecycle() throws Exception {
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

    /**
     * 현재 시각 기준 ISSUED/EXPIRED 및 발급 기간 필터 결과를 검증하고 연결 Offer가 없는 쿠폰의 부가 정보는 null인지 확인한다.
     * 역전된 발급 기간은 400 COUPON_LIST_FILTER_INVALID여야 한다.
     */
    @Test
    void filtersCouponStatusAndPeriod() throws Exception {
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

    /**
     * 내 쿠폰 상세가 발급·사용·만료 상태와 사용 시각을 반환하고 타인 쿠폰 및 없는 ID는 모두 404 COUPON_NOT_FOUND인지 검증한다.
     */
    @Test
    void returnsOwnedCouponCurrentStatus() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        User tourist = saveUser("couponDetailTourist", UserRole.USER);
        User otherTourist = saveUser("couponDetailOtherTourist", UserRole.USER);

        TouristCoupon issued = couponRepository.saveAndFlush(TouristCoupon.issue(
                201L, tourist.getId(), "detail-issued", now.minusMinutes(1), now.plusDays(1)
        ));
        TouristCoupon redeemed = TouristCoupon.issue(
                202L, tourist.getId(), "detail-redeemed", now.minusDays(1), now.plusDays(1)
        );
        redeemed.redeem(999L, now.minusMinutes(1));
        redeemed = couponRepository.saveAndFlush(redeemed);
        TouristCoupon expired = couponRepository.saveAndFlush(TouristCoupon.issue(
                203L, tourist.getId(), "detail-expired", now.minusDays(1), now.minusMinutes(1)
        ));

        mockMvc.perform(get("/coupons/{couponId}", issued.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(issued.getId()))
                .andExpect(jsonPath("$.status").value("ISSUED"));

        mockMvc.perform(get("/coupons/{couponId}", redeemed.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REDEEMED"))
                .andExpect(jsonPath("$.redeemedAt").isNotEmpty());

        mockMvc.perform(get("/coupons/{couponId}", expired.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));

        mockMvc.perform(get("/coupons/{couponId}", issued.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(otherTourist)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_FOUND"));

        mockMvc.perform(get("/coupons/{couponId}", Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_FOUND"));
    }

    /**
     * 진행 중 여행 일정이 없는 관광객의 발급 요청이 403 TOURIST_ELIGIBILITY_REQUIRED이며 쿠폰·발급 수가 증가하지 않는지 검증한다.
     */
    @Test
    void rejectsIssuanceWithoutTravelSchedule() throws Exception {
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

    /**
     * PUBLIC·UNLIMITED·OFFER_END 정책은 여행 일정 없이 목록 조회·발급이 가능하고 수량 필드를 생략하며 Offer 종료일에 만료되는지 검증한다.
     */
    @Test
    void issuesPublicOfferWithoutTravel() throws Exception {
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

    /**
     * Authorization 없는 Offer·쿠폰·점주 Offer 목록 요청이 모두 401로 거절되는지 검증한다.
     */
    @Test
    void offerEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/offers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/coupons")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/merchant-owner/offers")).andExpect(status().isUnauthorized());
    }

    /**
     * 점주가 접근 가능한 장소의 Offer만 조회하고 장소·게시 상태·페이지 조건이 적용되는지 검증한다.
     * 타 점주 장소 필터는 빈 목록, 잘못된 상태 문자열은 400이어야 한다.
     */
    @Test
    void filtersOwnedOffersWithoutLeaks() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        User merchant = saveUser("offerFilterMerchant", UserRole.MERCHANT_OWNER);
        User otherMerchant = saveUser("offerFilterOtherMerchant", UserRole.MERCHANT_OWNER);
        MapPlace firstPlace = savePlace(merchant, "첫 번째 필터 장소");
        MapPlace secondPlace = savePlace(merchant, "두 번째 필터 장소");
        MapPlace inaccessiblePlace = savePlace(merchant, "권한 없는 장소");
        MapPlace otherPlace = savePlace(otherMerchant, "다른 사장님 장소");
        activateMerchant(merchant, firstPlace, now);
        activateMerchant(merchant, secondPlace, now);
        activateMerchant(otherMerchant, otherPlace, now);

        saveOffer(merchant, firstPlace, "첫 번째 초안", OfferStatus.DRAFT, now);
        saveOffer(merchant, firstPlace, "첫 번째 게시", OfferStatus.PUBLISHED, now);
        saveOffer(merchant, secondPlace, "두 번째 게시", OfferStatus.PUBLISHED, now);
        saveOffer(merchant, inaccessiblePlace, "접근 불가 Offer", OfferStatus.DRAFT, now);
        saveOffer(otherMerchant, otherPlace, "다른 사장님 게시", OfferStatus.PUBLISHED, now);

        mockMvc.perform(get("/merchant-owner/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(merchant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/merchant-owner/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(merchant))
                        .param("placeId", firstPlace.getId().toString())
                        .param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.offers[0].title").value("첫 번째 게시"));

        mockMvc.perform(get("/merchant-owner/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(merchant))
                        .param("status", "PUBLISHED")
                        .param("page", "2")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.offers[0].title").value("첫 번째 게시"));

        mockMvc.perform(get("/merchant-owner/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(merchant))
                        .param("placeId", otherPlace.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/merchant-owner/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(merchant))
                        .param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 점주 탈퇴 데이터 정리 후 Offer는 CLOSED가 되고 다른 관광객의 쿠폰 1건은 보존되는지 검증한다.
     */
    @Test
    void merchantWithdrawalPreservesTouristCoupons() {
        WithdrawalFixture fixture = withdrawalFixture();

        userWithdrawalDataService.cleanupUserOwnedData(fixture.merchant().getId());

        org.assertj.core.api.Assertions.assertThat(
                offerRepository.findById(fixture.offer().getId()).orElseThrow().getStatus()
        ).isEqualTo(OfferStatus.CLOSED);
        org.assertj.core.api.Assertions.assertThat(couponRepository.findAll()).hasSize(1);
    }

    /**
     * 관광객 탈퇴 데이터 정리 후 본인 쿠폰은 삭제하고 점주의 Offer는 PUBLISHED로 유지되는지 검증한다.
     */
    @Test
    void touristWithdrawalPreservesMerchantOffer() {
        WithdrawalFixture fixture = withdrawalFixture();

        userWithdrawalDataService.cleanupUserOwnedData(fixture.tourist().getId());

        org.assertj.core.api.Assertions.assertThat(
                offerRepository.findById(fixture.offer().getId()).orElseThrow().getStatus()
        ).isEqualTo(OfferStatus.PUBLISHED);
        org.assertj.core.api.Assertions.assertThat(couponRepository.findAll()).isEmpty();
    }

    /**
     * 활성 점주·관광객·게시 Offer·발급 쿠폰 관계를 DB에 만들어 탈퇴 주체별 정리 범위를 재현한다.
     */
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

    /**
     * 수량 1 Offer에 두 관광객이 barrier 이후 동시에 발급을 시도하면 성공 1건·OFFER_SOLD_OUT 1건과 저장 쿠폰/발급 수 1을 검증한다.
     * Future 대기 시간을 제한하고 executor를 종료해 경합 실패 시에도 자원을 회수한다.
     */
    @Test
    void limitsConcurrentCouponIssuance() throws Exception {
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

    /**
     * 두 발급 작업을 barrier에서 맞춘 뒤 서비스를 호출하고 OfferException은 오류 코드로 반환해 경합 결과를 함께 비교한다.
     */
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

    /**
     * 주어진 사용자명·역할로 한국어·한국 국가의 테스트 사용자를 저장하고 JWT 발급 대상을 제공한다.
     */
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

    /**
     * 점주가 등록한 고정 좌표의 장소를 주어진 이름으로 저장한다. 실제 소유 관계는 활성화 helper에서 별도로 설정한다.
     */
    private MapPlace savePlace(User merchant, String name) {
        return mapPlaceRepository.saveAndFlush(MapPlace.builder()
                .name(name)
                .address("서울시 중구")
                .latitude(37.5665)
                .longitude(126.9780)
                .userId(merchant.getId())
                .registrant(merchant.getUsername())
                .build());
    }

    /**
     * 점주·장소의 유효 기간 내 Offer를 생성하고 요청 상태에 맞게 게시·종료한 후 저장한다.
     */
    private void saveOffer(User merchant, MapPlace place, String title, OfferStatus status, LocalDateTime now) {
        TouristOffer offer = TouristOffer.draft(
                merchant.getId(),
                place.getId(),
                title,
                "설명",
                "혜택",
                now.minusHours(1),
                now.plusDays(3),
                10,
                1,
                now.minusDays(1)
        );
        if (status == OfferStatus.PUBLISHED || status == OfferStatus.CLOSED) {
            offer.publish(now);
        }
        if (status == OfferStatus.CLOSED) {
            offer.close(now);
        }
        offerRepository.saveAndFlush(offer);
    }

    /**
     * 점주 프로필 승인·본인/사업자 검증 승인·장소 소유 관계를 저장해 Offer 운영 자격 조건을 충족시킨다.
     */
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

    /**
     * 저장된 사용자의 ID·이름·역할로 액세스 토큰을 만들고 Authorization 헤더용 Bearer 접두사를 붙인다.
     */
    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUsername(),
                user.getRole().name()
        );
    }

    /**
     * 외래 참조의 종속 데이터를 먼저 지우도록 쿠폰·Offer·여행 일정·점주 관계·장소·사용자 순서로 정리한다.
     */
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
