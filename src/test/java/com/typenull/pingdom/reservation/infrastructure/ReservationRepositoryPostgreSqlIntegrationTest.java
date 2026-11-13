package com.typenull.pingdom.reservation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.reservation.application.ReservationService;
import com.typenull.pingdom.reservation.api.dto.AdminReservationResponse;
import com.typenull.pingdom.reservation.domain.Reservation;
import com.typenull.pingdom.reservation.domain.ReservationStatus;
import com.typenull.pingdom.reservation.domain.ReservationStatusHistory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("postgres-integration")
@Tag("postgres-smoke")
@Testcontainers
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.cloud.aws.s3.enabled=false",
        "management.health.redis.enabled=false",
        "fcm.enabled=false",
        "outbox.enabled=false"
})
class ReservationRepositoryPostgreSqlIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    /**
     * PostGIS 컨테이너의 JDBC 주소·계정·PostgreSQL 드라이버를 Spring 데이터소스에 연결.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired private ReservationService reservationService;
    @Autowired private ReservationStatusHistoryRepository histories;
    @Autowired private PlaceAvailabilityRepository slots;
    @Autowired private MerchantOwnerProfileRepository profiles;
    @Autowired private MerchantOwnerPlaceRepository owners;
    @Autowired private UserRepository users;
    @Autowired private MapPlaceRepository places;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    /**
     * 실제 PostgreSQL에서 선택 기간 필터를 null·비활성 플래그로 전달해 관리자 예약 조회가 빈 결과로 정상 실행되는지 검증.
     * nullable 기간 바인딩의 타입 추론 오류를 방지하는 회귀 테스트.
     */
    @Test
    void queriesWithoutOptionalPeriodFilters() {
        var result = reservationRepository.findAllForAdmin(ReservationStatus.PENDING, null, null, null, false, null, false, null, PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @Transactional
    void adminPageBatchesDifferentRelationsAndPreservesHistoryOrder() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 12, 12, 0);
        List<Long> reservationIds = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            User tourist = user(UserRole.USER);
            User owner = user(UserRole.MERCHANT_OWNER);
            profiles.save(MerchantOwnerProfile.builder().userId(owner.getId()).businessName("상점")
                    .displayName("현재 점주").contactEmail(owner.getEmail()).contactPhone("01012345678")
                    .status(MerchantOwnerStatus.ACTIVE).createdAt(now).updatedAt(now).build());
            MapPlace place = places.save(MapPlace.builder().name("예약 장소 " + index).address("서울")
                    .latitude(37.56).longitude(126.97).userId(owner.getId()).registrant(owner.getUsername()).build());
            owners.save(MerchantOwnerPlace.builder().placeId(place.getId()).merchantOwnerUserId(owner.getId())
                    .createdAt(now).build());
            PlaceAvailability slot = slots.save(PlaceAvailability.create(owner.getId(), place.getId(),
                    now.plusDays(1), now.plusDays(1).plusHours(1), 10, now));
            Reservation reservation = Reservation.create(tourist.getId(), slot.getId(), "batch-read", 1,
                    slot.getStartsAt(), slot.getEndsAt(), "예약자", "01012345678", null, now);
            reservation.confirm(owner.getId(), "확인", now);
            reservation = reservationRepository.save(reservation);
            reservationIds.add(reservation.getId());
            histories.save(ReservationStatusHistory.of(reservation.getId(), ReservationStatus.PENDING,
                    tourist.getId(), "접수", now));
            histories.save(ReservationStatusHistory.of(reservation.getId(), ReservationStatus.CONFIRMED,
                    owner.getId(), "확인", now));
        }
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var single = reservationService.listForAdmin(ReservationStatus.CONFIRMED, null, null, null,
                null, null, 1, 1);
        long singleStatements = statistics.getPrepareStatementCount();

        entityManager.clear();
        statistics.clear();
        var page = reservationService.listForAdmin(ReservationStatus.CONFIRMED, null, null, null,
                null, null, 1, 20);
        long pageStatements = statistics.getPrepareStatementCount();

        assertThat(single.reservations().getFirst().id()).isEqualTo(reservationIds.getLast());
        assertThat(page.reservations()).extracting(AdminReservationResponse::id)
                .containsExactlyElementsOf(reservationIds.reversed());
        assertThat(page.totalElements()).isEqualTo(20);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.reservations()).allSatisfy(item -> {
            assertThat(item.statusHistory()).extracting(history -> history.status())
                    .containsExactly(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
            assertThat(item.statusHistory().get(1).changedBy()).isEqualTo(item.merchantOwnerUserId());
        });
        assertThat(singleStatements).isLessThanOrEqualTo(7L);
        assertThat(pageStatements).as("관계가 다른 20개 예약도 페이지/count와 5개 묶음 조회로 제한한다")
                .isLessThanOrEqualTo(7L);
        assertThat(pageStatements).isLessThanOrEqualTo(singleStatements);
    }

    private User user(UserRole role) {
        String name = UUID.randomUUID().toString();
        return users.save(User.builder().username(name).email(name + "@example.com").emailVerified(true)
                .password("encoded-password").birthYear(1990).language("ko").country("KR")
                .role(role).status(UserStatus.ACTIVE).build());
    }
}
