package com.typenull.pingdom.integration.concurrency;

import static com.typenull.pingdom.verification.VerificationSecurityFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.verification.api.dto.LocationCheckInRequest;
import com.typenull.pingdom.verification.api.dto.LocationCheckInResponse;
import com.typenull.pingdom.verification.api.dto.VisitEvidenceResponse;
import com.typenull.pingdom.verification.application.LocationCheckInService;
import com.typenull.pingdom.verification.application.VisitEvidenceService;
import com.typenull.pingdom.verification.domain.LocationCheckIn;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationErrorCode;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationException;
import com.typenull.pingdom.verification.infrastructure.LocationCheckInRepository;
import com.typenull.pingdom.verification.infrastructure.VisitEvidenceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("postgres-integration")
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
        "outbox.enabled=false",
        "verification.visit-evidence.max-file-size-bytes=1024"
})
class VerificationConcurrencyIntegrationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
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

    @Autowired private LocationCheckInService checkInService;
    @Autowired private VisitEvidenceService evidenceService;
    @Autowired private UserRepository userRepository;
    @Autowired private MapPlaceRepository placeRepository;
    @Autowired private LocationCheckInRepository checkInRepository;
    @Autowired private VisitEvidenceRepository evidenceRepository;

    @MockBean
    private S3ObjectStorage objectStorage;

    @BeforeEach
    void cleanDatabase() {
        evidenceRepository.deleteAllInBatch();
        checkInRepository.deleteAllInBatch();
        placeRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        reset(objectStorage);
    }

    @Test
    void concurrentDailyCheckInCreatesOneRecordAndMapsConstraintFailure() throws Exception {
        User tourist = userRepository.saveAndFlush(user("concurrentCheckIn", UserRole.USER));
        MapPlace savedPlace = placeRepository.saveAndFlush(place(tourist.getId()));
        Instant observedAt = Instant.now();
        LocationCheckInRequest request = new LocationCheckInRequest(
                savedPlace.getId(), PLACE_LATITUDE, PLACE_LONGITUDE, 10.0, observedAt);

        List<ConcurrentScenario.Result<LocationCheckInResponse>> results = ConcurrentScenario.run(
                TIMEOUT,
                () -> checkInService.checkIn(tourist.getId(), request),
                () -> checkInService.checkIn(tourist.getId(), request));

        assertThat(results).filteredOn(ConcurrentScenario.Result::succeeded).hasSize(1);
        assertThat(results).filteredOn(result -> !result.succeeded())
                .extracting(ConcurrentScenario.Result::failure)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(VisitorVerificationException.class, exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(VisitorVerificationErrorCode.DAILY_CHECK_IN_ALREADY_EXISTS)));
        assertThat(checkInRepository.count()).isOne();
    }

    @Test
    void concurrentEvidenceUploadKeepsOneRowAndDeletesOnlyLosingObject() throws Exception {
        User tourist = userRepository.saveAndFlush(user("concurrentEvidence", UserRole.USER));
        MapPlace savedPlace = placeRepository.saveAndFlush(place(tourist.getId()));
        LocationCheckIn savedCheckIn = checkInRepository.saveAndFlush(
                checkIn(tourist.getId(), savedPlace.getId(), Instant.now()));
        AtomicInteger keySequence = new AtomicInteger();
        when(objectStorage.put(any(byte[].class), anyString(), eq("image/jpeg"), eq("visit-evidence")))
                .thenAnswer(invocation -> new S3ObjectStorage.S3PutResult(
                        "visit-evidence/concurrent-" + keySequence.incrementAndGet(), "unused"));

        List<ConcurrentScenario.Result<VisitEvidenceResponse>> results = ConcurrentScenario.run(
                TIMEOUT,
                () -> evidenceService.upload(tourist.getId(), savedCheckIn.getId(), jpeg()),
                () -> evidenceService.upload(tourist.getId(), savedCheckIn.getId(), jpeg()));

        assertThat(results).filteredOn(ConcurrentScenario.Result::succeeded).hasSize(1);
        assertThat(results).filteredOn(result -> !result.succeeded())
                .extracting(ConcurrentScenario.Result::failure)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(VisitorVerificationException.class, exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(VisitorVerificationErrorCode.VISIT_EVIDENCE_ALREADY_EXISTS)));
        assertThat(evidenceRepository.count()).isOne();
        ArgumentCaptor<String> deletedKey = ArgumentCaptor.forClass(String.class);
        verify(objectStorage).delete(deletedKey.capture());
        assertThat(deletedKey.getValue()).startsWith("visit-evidence/concurrent-");
        assertThat(evidenceRepository.findAll().getFirst().getS3Key()).isNotEqualTo(deletedKey.getValue());
    }

    private MockMultipartFile jpeg() throws Exception {
        return new MockMultipartFile("file", "visit.jpg", "image/jpeg", jpegBytes());
    }
}
