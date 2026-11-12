package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerProfileResponse;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.LongStream;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@Tag("integration")
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.typenull.pingdom.identity.application.service.merchant.MerchantOwnerAdminListQueryIntegrationTest$SqlCapture"
})
@ContextConfiguration(classes = MerchantOwnerAdminListQueryIntegrationTest.Config.class)
class MerchantOwnerAdminListQueryIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 10, 0);

    @Autowired private MerchantOwnerProfileRepository profileRepository;
    @Autowired private MerchantOwnerPlaceRepository ownerPlaceRepository;
    @Autowired private EntityManager entityManager;

    private MerchantOwnerAdminService service;

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = MerchantOwnerProfile.class)
    @EnableJpaRepositories(
            basePackageClasses = MerchantOwnerPlaceRepository.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = {MerchantOwnerProfileRepository.class, MerchantOwnerPlaceRepository.class})
    )
    static class Config {
    }

    @BeforeEach
    void setUp() {
        service = new MerchantOwnerAdminService(
                mock(UserRepository.class),
                profileRepository,
                ownerPlaceRepository,
                mock(MapPlaceRepository.class),
                mock(AdminAuditLogService.class),
                mock(UserAccessStatusService.class),
                mock(TouristOfferRepository.class),
                mock(ApplicationEventPublisher.class),
                Clock.systemUTC(),
                mock(AdminRoleAuthorizationService.class),
                mock(PlaceRegistrationApplicationRepository.class)
        );
        SqlCapture.reset();
    }

    @AfterEach
    void clearCapture() {
        SqlCapture.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 100})
    void usesOnePlaceSelectForEachNonEmptyProfilePage(int pageSize) {
        for (long ownerId = 1; ownerId <= 101; ownerId++) {
            entityManager.persist(profile(ownerId, false));
            if (ownerId % 3 != 0) {
                // 입력 순서와 무관하게 저장소의 placeId 정렬이 유지되는지 확인합니다.
                entityManager.persist(mapping(ownerId, ownerId * 10 + 2));
                entityManager.persist(mapping(ownerId, ownerId * 10 + 1));
            }
        }
        prepareQueryMeasurement();

        var response = service.list(null, 1, pageSize);

        List<Long> expectedOwners = LongStream.iterate(101, value -> value - 1).limit(pageSize).boxed().toList();
        assertThat(response.profiles()).extracting(MerchantOwnerProfileResponse::userId)
                .containsExactlyElementsOf(expectedOwners);
        assertThat(response.profiles()).allSatisfy(profile -> assertThat(profile.placeIds())
                .containsExactlyElementsOf(profile.userId() % 3 == 0 ? List.of()
                        : List.of(profile.userId() * 10 + 1, profile.userId() * 10 + 2)));
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.limit()).isEqualTo(pageSize);
        assertThat(response.totalCount()).isEqualTo(101);
        assertThat(response.totalPages()).isEqualTo((101 + pageSize - 1) / pageSize);
        assertThat(response.hasNext()).isTrue();
        assertThat(placeSelectCount()).isEqualTo(1);
        // count 쿼리가 생략 가능한 페이지에서도 연결 조회의 상한은 같아야 합니다.
        assertThat(SqlCapture.statements().stream().filter(sql -> sql.startsWith("select")).count())
                .isLessThanOrEqualTo(3);
    }

    @Test
    void preservesStatusFilteringAndUserIdTieBreakerAcrossPages() {
        entityManager.persist(profile(7L, true));
        entityManager.persist(profile(8L, true));
        entityManager.persist(profile(9L, false));
        entityManager.persist(mapping(7L, 70L));
        entityManager.persist(mapping(8L, 80L));
        entityManager.persist(mapping(9L, 90L));
        prepareQueryMeasurement();

        var first = service.list(MerchantOwnerStatus.ACTIVE, 1, 1);
        var second = service.list(MerchantOwnerStatus.ACTIVE, 2, 1);

        assertThat(first.profiles()).extracting(MerchantOwnerProfileResponse::userId).containsExactly(8L);
        assertThat(first.profiles().get(0).placeIds()).containsExactly(80L);
        assertThat(second.profiles()).extracting(MerchantOwnerProfileResponse::userId).containsExactly(7L);
        assertThat(second.profiles().get(0).placeIds()).containsExactly(70L);
        assertThat(first.totalCount()).isEqualTo(2);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(second.page()).isEqualTo(2);
        assertThat(second.hasNext()).isFalse();
        assertThat(placeSelectCount()).isEqualTo(2);
    }

    @Test
    void skipsPlaceSelectWhenThePageHasNoProfiles() {
        prepareQueryMeasurement();

        var response = service.list(null, 1, 20);

        assertThat(response.profiles()).isEmpty();
        assertThat(response.totalCount()).isZero();
        assertThat(response.hasNext()).isFalse();
        assertThat(placeSelectCount()).isZero();
    }

    private void prepareQueryMeasurement() {
        entityManager.flush();
        entityManager.clear();
        SqlCapture.reset();
    }

    private long placeSelectCount() {
        return SqlCapture.statements().stream()
                .filter(sql -> sql.startsWith("select") && sql.contains(" from merchant_owner_place "))
                .count();
    }

    private MerchantOwnerProfile profile(long ownerId, boolean active) {
        MerchantOwnerProfile profile = MerchantOwnerProfile.pending(
                ownerId, "사업장 " + ownerId, "점주 " + ownerId, null,
                "owner" + ownerId + "@example.com", "010-0000-0000", NOW
        );
        if (active) {
            profile.approve(99L, NOW);
        }
        return profile;
    }

    private MerchantOwnerPlace mapping(long ownerId, long placeId) {
        return MerchantOwnerPlace.builder()
                .placeId(placeId).merchantOwnerUserId(ownerId).createdAt(NOW).build();
    }

    public static class SqlCapture implements StatementInspector {
        private static final ThreadLocal<List<String>> STATEMENTS = ThreadLocal.withInitial(ArrayList::new);

        @Override
        public String inspect(String sql) {
            STATEMENTS.get().add(sql.toLowerCase(Locale.ROOT));
            return sql;
        }

        static void reset() {
            STATEMENTS.get().clear();
        }

        static List<String> statements() {
            return List.copyOf(STATEMENTS.get());
        }

        static void clear() {
            STATEMENTS.remove();
        }
    }
}
