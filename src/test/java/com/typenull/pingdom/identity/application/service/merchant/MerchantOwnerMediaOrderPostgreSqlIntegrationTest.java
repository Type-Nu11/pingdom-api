package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaOrderUpdateRequest;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMediaUploadRepository;
import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingHoursEvaluator;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.media.PlaceMedia;
import com.typenull.pingdom.place.domain.place.media.PlaceMediaPurpose;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceMediaRepository;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
        "spring.cloud.aws.s3.enabled=false",
        "management.health.redis.enabled=false",
        "fcm.enabled=false",
        "outbox.enabled=false"
})
class MerchantOwnerMediaOrderPostgreSqlIntegrationTest {

    private static final Long USER_ID = 20L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-31T03:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    @Autowired private MapPlaceRepository mapPlaceRepository;
    @Autowired private PlaceMediaRepository placeMediaRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private MerchantOwnerPlaceManagementService service;
    private TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM place_media");
        jdbcTemplate.update("DELETE FROM map_place");
        transactionTemplate = new TransactionTemplate(transactionManager);

        MerchantPlaceCapabilityPolicy capabilityPolicy = mock(MerchantPlaceCapabilityPolicy.class);
        doNothing().when(capabilityPolicy).require(any(), any(), any());
        service = new MerchantOwnerPlaceManagementService(
                mapPlaceRepository,
                placeMediaRepository,
                mock(MerchantPlaceMediaUploadRepository.class),
                capabilityPolicy,
                mock(PlaceOperatingHoursEvaluator.class),
                mock(S3ObjectStorage.class),
                mock(S3ObjectDeleteOutboxPublisher.class),
                CLOCK
        );
    }

    @Test
    void concurrentOrderChangesKeepUniqueAndContinuousDisplayOrders() throws Exception {
        MapPlace place = mapPlaceRepository.saveAndFlush(MapPlace.builder()
                .name("동시성 검증 장소")
                .address("경상남도 진주시 테스트로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(USER_ID)
                .registrant("merchant")
                .build());
        List<PlaceMedia> media = placeMediaRepository.saveAllAndFlush(List.of(
                explorationMedia(place, "first.jpg", 0),
                explorationMedia(place, "second.jpg", 1),
                explorationMedia(place, "third.jpg", 2)
        ));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> moveLastToFirst = executor.submit(() -> updateAfterStart(
                    ready, start, place.getId(), media.get(2).getId(), 0
            ));
            Future<?> moveFirstToLast = executor.submit(() -> updateAfterStart(
                    ready, start, place.getId(), media.get(0).getId(), 2
            ));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            moveLastToFirst.get(10, TimeUnit.SECONDS);
            moveFirstToLast.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(placeMediaRepository.findAllByPlace_IdAndPurposeOrderByDisplayOrderAscIdAsc(
                place.getId(),
                PlaceMediaPurpose.EXPLORATION
        )).extracting(PlaceMedia::getDisplayOrder).containsExactly(0, 1, 2);
    }

    private void updateAfterStart(
            CountDownLatch ready,
            CountDownLatch start,
            Long placeId,
            Long mediaId,
            int displayOrder
    ) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 순서 변경 시작 대기 시간이 초과되었습니다.");
            }
            transactionTemplate.executeWithoutResult(status -> service.updateMediaOrder(
                    USER_ID,
                    placeId,
                    mediaId,
                    new MerchantOwnerMediaOrderUpdateRequest(displayOrder)
            ));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시 순서 변경이 중단되었습니다.", exception);
        }
    }

    private PlaceMedia explorationMedia(MapPlace place, String filename, int displayOrder) {
        return PlaceMedia.exploration(
                place,
                "https://cdn.pingdom.test/" + filename,
                "places/" + place.getId() + "/exploration/20/" + filename,
                null,
                null,
                displayOrder,
                CLOCK.instant().atOffset(ZoneOffset.UTC).toLocalDateTime()
        );
    }
}
