package com.typenull.pingdom.offer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.offer.domain.OfferStatus;
import com.typenull.pingdom.offer.domain.TouristCoupon;
import com.typenull.pingdom.offer.domain.TouristOffer;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cloud.aws.s3.enabled=false",
        "management.health.redis.enabled=false",
        "fcm.enabled=false",
        "outbox.enabled=false"
})
class PlaceMerchantOfferRepositoryIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 26, 12, 0);

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private UserRepository userRepository;
    @Autowired private MapPlaceRepository placeRepository;
    @Autowired private MerchantOwnerProfileRepository profileRepository;
    @Autowired private MerchantVerificationRepository verificationRepository;
    @Autowired private MerchantOwnerPlaceRepository ownerPlaceRepository;
    @Autowired private TouristOfferRepository offerRepository;
    @Autowired private TouristCouponRepository couponRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        couponRepository.deleteAllInBatch();
        offerRepository.deleteAllInBatch();
        ownerPlaceRepository.deleteAllInBatch();
        verificationRepository.deleteAllInBatch();
        profileRepository.deleteAllInBatch();
        placeRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void availableOfferRequiresActiveVerifiedMerchantAndOwnedPlace() {
        MerchantContext active = merchant("active", UserRole.MERCHANT_OWNER, UserStatus.ACTIVE, false,
                MerchantOwnerStatus.ACTIVE, MerchantVerificationStatus.APPROVED);
        MerchantContext pendingProfile = merchant("pending-profile", UserRole.MERCHANT_OWNER, UserStatus.ACTIVE, false,
                MerchantOwnerStatus.PENDING, MerchantVerificationStatus.APPROVED);
        MerchantContext pendingVerification = merchant("pending-verification", UserRole.MERCHANT_OWNER,
                UserStatus.ACTIVE, false, MerchantOwnerStatus.ACTIVE, MerchantVerificationStatus.PENDING);
        MerchantContext wrongRole = merchant("wrong-role", UserRole.USER, UserStatus.ACTIVE, false,
                MerchantOwnerStatus.ACTIVE, MerchantVerificationStatus.APPROVED);
        MerchantContext withdrawn = merchant("withdrawn", UserRole.MERCHANT_OWNER, UserStatus.WITHDRAWN, false,
                MerchantOwnerStatus.ACTIVE, MerchantVerificationStatus.APPROVED);
        MerchantContext banned = merchant("banned", UserRole.MERCHANT_OWNER, UserStatus.ACTIVE, true,
                MerchantOwnerStatus.ACTIVE, MerchantVerificationStatus.APPROVED);
        MerchantContext otherOwner = merchant("other-owner", UserRole.MERCHANT_OWNER, UserStatus.ACTIVE, false,
                MerchantOwnerStatus.ACTIVE, MerchantVerificationStatus.APPROVED);

        TouristOffer available = savePublished(active, "available", NOW.minusHours(1), NOW.plusHours(1), 2);
        TouristOffer startsNow = savePublished(active, "starts-now", NOW, NOW.plusHours(1), 2);
        savePublished(pendingProfile, "pending-profile", NOW.minusHours(1), NOW.plusHours(1), 2);
        savePublished(pendingVerification, "pending-verification", NOW.minusHours(1), NOW.plusHours(1), 2);
        savePublished(wrongRole, "wrong-role", NOW.minusHours(1), NOW.plusHours(1), 2);
        savePublished(withdrawn, "withdrawn", NOW.minusHours(1), NOW.plusHours(1), 2);
        savePublished(banned, "banned", NOW.minusHours(1), NOW.plusHours(1), 2);
        offerRepository.saveAndFlush(PlaceMerchantOfferFixture.publishedOffer(
                active.user().getId(), otherOwner.place().getId(), "wrong-owner",
                NOW.minusHours(1), NOW.plusHours(1), 2, NOW.minusHours(1)
        ));
        savePublished(active, "future", NOW.plusMinutes(1), NOW.plusHours(1), 2);
        savePublished(active, "expired", NOW.minusDays(2), NOW.minusDays(1), 2);
        savePublished(active, "ends-now", NOW.minusHours(1), NOW, 2);
        TouristOffer closed = savePublished(active, "closed", NOW.minusHours(1), NOW.plusHours(1), 2);
        closed.close(NOW.minusMinutes(1));
        offerRepository.saveAndFlush(closed);
        TouristOffer soldOut = savePublished(active, "sold-out", NOW.minusHours(1), NOW.plusHours(1), 1);
        soldOut.issueCoupon(NOW.minusMinutes(1));
        offerRepository.saveAndFlush(soldOut);

        List<TouristOffer> result = offerRepository.findAvailable(
                OfferStatus.PUBLISHED,
                NOW,
                null,
                PageRequest.of(0, 20)
        ).getContent();

        assertThat(result)
                .as("활성 사용자·프로필·검증·소유권·기간·재고 조건을 모두 만족한 Offer만 노출되어야 한다")
                .extracting(TouristOffer::getId)
                .containsExactlyInAnyOrder(available.getId(), startsNow.getId());
        assertThat(offerRepository.findPlaceIdsWithAvailableOffers(
                List.of(
                        active.place().getId(), pendingProfile.place().getId(), pendingVerification.place().getId(),
                        wrongRole.place().getId(), withdrawn.place().getId(), banned.place().getId(),
                        otherOwner.place().getId()
                ),
                NOW
        )).containsExactly(active.place().getId());
    }

    @Test
    void duplicateCouponRollbackAllowsRetryForAnotherTourist() {
        MerchantContext merchant = merchant("coupon-owner", UserRole.MERCHANT_OWNER, UserStatus.ACTIVE, false,
                MerchantOwnerStatus.ACTIVE, MerchantVerificationStatus.APPROVED);
        TouristOffer offer = savePublished(merchant, "coupon", NOW.minusHours(1), NOW.plusDays(1), 2);
        User firstTourist = userRepository.saveAndFlush(PlaceMerchantOfferFixture.user("tourist-1", UserRole.USER));
        User retryTourist = userRepository.saveAndFlush(PlaceMerchantOfferFixture.user("tourist-2", UserRole.USER));
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        requiresNew.executeWithoutResult(status -> couponRepository.saveAndFlush(
                PlaceMerchantOfferFixture.coupon(offer.getId(), firstTourist.getId(), "coupon-code-1", NOW)
        ));

        assertThatThrownBy(() -> requiresNew.executeWithoutResult(status -> couponRepository.saveAndFlush(
                PlaceMerchantOfferFixture.coupon(offer.getId(), firstTourist.getId(), "coupon-code-duplicate", NOW)
        )))
                .as("동일 Offer와 사용자 조합의 중복 쿠폰은 DB 유일 제약으로 차단되어야 한다")
                .isInstanceOf(DataIntegrityViolationException.class);

        requiresNew.executeWithoutResult(status -> couponRepository.saveAndFlush(
                PlaceMerchantOfferFixture.coupon(offer.getId(), retryTourist.getId(), "coupon-code-retry", NOW)
        ));

        assertThat(couponRepository.findAll())
                .as("실패 transaction rollback 후 독립된 재시도는 정상 저장되어야 한다")
                .extracting(TouristCoupon::getUserId)
                .containsExactlyInAnyOrder(firstTourist.getId(), retryTourist.getId());
    }

    private MerchantContext merchant(
            String suffix,
            UserRole role,
            UserStatus userStatus,
            boolean banned,
            MerchantOwnerStatus profileStatus,
            MerchantVerificationStatus verificationStatus
    ) {
        User user = userRepository.saveAndFlush(PlaceMerchantOfferFixture.user(suffix, role, userStatus, banned));
        MapPlace place = placeRepository.saveAndFlush(PlaceMerchantOfferFixture.place(user.getId(), suffix));
        profileRepository.saveAndFlush(PlaceMerchantOfferFixture.profile(user.getId(), profileStatus, NOW));
        verificationRepository.saveAndFlush(
                PlaceMerchantOfferFixture.verification(user.getId(), verificationStatus, NOW)
        );
        ownerPlaceRepository.saveAndFlush(PlaceMerchantOfferFixture.ownership(user.getId(), place.getId(), NOW));
        return new MerchantContext(user, place);
    }

    private TouristOffer savePublished(
            MerchantContext merchant,
            String suffix,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            int quantity
    ) {
        return offerRepository.saveAndFlush(PlaceMerchantOfferFixture.publishedOffer(
                merchant.user().getId(),
                merchant.place().getId(),
                suffix,
                startsAt,
                endsAt,
                quantity,
                startsAt
        ));
    }

    private record MerchantContext(User user, MapPlace place) {
    }

}
