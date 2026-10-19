package com.typenull.pingdom.integration.review;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.api.dto.review.*;
import com.typenull.pingdom.place.application.service.review.PlaceReviewService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.review.*;
import com.typenull.pingdom.place.infrastructure.persistence.place.*;
import com.typenull.pingdom.shared.exception.*;
import com.typenull.pingdom.verification.domain.*;
import com.typenull.pingdom.verification.infrastructure.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
@Testcontainers
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false", "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cloud.aws.s3.enabled=false", "management.health.redis.enabled=false",
        "fcm.enabled=false", "outbox.enabled=false"
})
class PlaceReviewManagementPostgreSqlIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("pingdom").withUsername("pingdom").withPassword("pingdom");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired PlaceReviewService service;
    @Autowired com.typenull.pingdom.place.application.service.review.PlaceReviewMediaService mediaService;
    @Autowired MapPlaceRepository places;
    @SpyBean PlaceReviewRepository reviews;
    @Autowired PlaceReviewMediaUploadRepository media;
    @Autowired PlaceReviewDeletionRequestRepository deletionRequests;
    @Autowired UserRepository users;
    @Autowired LocationCheckInRepository checkIns;
    @Autowired VisitVerificationSessionRepository sessions;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    @Test
    void swapsPhotoAndRecommendationOrderUnderImmediateUniqueConstraints() {
        Fixture f = fixture();
        var updated = service.update(f.user(), f.place(), f.review(), new PlaceReviewUpdateRequest(
                null, List.of(PlaceReviewRecommendReason.CLEAN, PlaceReviewRecommendReason.FRIENDLY), "수정",
                List.of(f.second(), f.first()), null));
        assertThat(updated.reviewMedia()).extracting(PlaceReviewMediaResponse::reviewMediaId).containsExactly(f.second(), f.first());
        var persisted = snapshot(f.review());
        assertThat(persisted.recommendReasons()).containsExactly(PlaceReviewRecommendReason.CLEAN, PlaceReviewRecommendReason.FRIENDLY);
        assertThat(persisted.recommendReason()).isEqualTo("CLEAN");
        assertThat(persisted.reviewMedia()).extracting(PlaceReviewMediaResponse::reviewMediaId).containsExactly(f.second(), f.first());
        assertThat(jdbc.queryForList("select display_order from place_review_media_upload where review_id=? order by display_order", Integer.class, f.review())).containsExactly(0, 1);
    }

    @Test
    void keepsSelectedPhotoAddsFreshPhotoAndPublishesRemovedObjectDeletion() {
        Fixture f = fixture();
        Long fresh = upload(f.user(), f.place());
        service.update(f.user(), f.place(), f.review(), new PlaceReviewUpdateRequest(null, null, null, List.of(fresh, f.second()), null));
        assertThat(media.existsById(f.first())).isFalse();
        assertThat(snapshot(f.review()).reviewMedia()).extracting(PlaceReviewMediaResponse::reviewMediaId).containsExactly(fresh, f.second());
        assertThat(jdbc.queryForObject("select count(*) from outbox_event where aggregate_type='PLACE_REVIEW_MEDIA' and aggregate_id=?", Long.class, f.first().toString())).isEqualTo(1L);
        service.update(f.user(), f.place(), f.review(), new PlaceReviewUpdateRequest(null, null, null, List.of(), null));
        assertThat(snapshot(f.review()).imageUrls()).isEmpty();
        assertThat(snapshot(f.review()).reviewMedia()).isEmpty();
    }

    @Test
    void rollsBackPhotoOrderRemovalOutboxAndRecommendationsWhenLaterWriteFails() {
        Fixture f = fixture();
        long outboxBefore = jdbc.queryForObject("select count(*) from outbox_event", Long.class);
        doThrow(new IllegalStateException("태그 저장 실패 재현")).when(reviews).flush();
        assertThatThrownBy(() -> service.update(f.user(), f.place(), f.review(), new PlaceReviewUpdateRequest(
                null, List.of(PlaceReviewRecommendReason.CLEAN), "실패할 변경", List.of(f.second()), null)))
                .isInstanceOf(IllegalStateException.class);
        var original = snapshot(f.review());
        assertThat(original.content()).isEqualTo("원본");
        assertThat(original.recommendReasons()).containsExactly(PlaceReviewRecommendReason.FRIENDLY, PlaceReviewRecommendReason.CLEAN);
        assertThat(original.reviewMedia()).extracting(PlaceReviewMediaResponse::reviewMediaId).containsExactly(f.first(), f.second());
        assertThat(jdbc.queryForObject("select count(*) from outbox_event", Long.class)).isEqualTo(outboxBefore);
        assertThat(jdbc.queryForList("select display_order from place_review_media_upload where review_id=? order by display_order", Integer.class, f.review())).containsExactly(0, 1);
    }

    @Test
    void ownDeletionPreservesPendingEvidenceVerifiedVisitAndAllowsRewriting() {
        Fixture f = fixture();
        Instant now = clock.instant();
        var checkIn = checkIns.saveAndFlush(LocationCheckIn.dwellVerified(f.user(), f.place(), LocalDate.now(clock), now, now, 1));
        var session = VisitVerificationSession.start(f.user(), f.place(), LocalDate.now(clock), now.minusSeconds(30), now.minusSeconds(30), 1, 100, Duration.ofSeconds(30), Duration.ofMinutes(5));
        session.complete(now, checkIn.getId());
        Long sessionId = sessions.saveAndFlush(session).getId();
        Long requestId = new TransactionTemplate(transactionManager).execute(status -> {
            var review = reviews.findById(f.review()).orElseThrow();
            review.hide(LocalDateTime.now(clock));
            return deletionRequests.saveAndFlush(PlaceReviewDeletionRequest.submit(review, f.user(), "심사 증거 보존", LocalDateTime.now(clock))).getId();
        });
        assertCode(() -> service.update(f.user(), f.place(), f.review(), new PlaceReviewUpdateRequest(null, null, "수정", null, null)), MapErrorCode.PLACE_REVIEW_UPDATE_PENDING_DELETION);
        service.delete(f.user(), f.place(), f.review());
        LocalDateTime firstDeletion = reviews.findById(f.review()).orElseThrow().getDeletedAt();
        service.delete(f.user(), f.place(), f.review());
        assertThat(reviews.findById(f.review()).orElseThrow().getDeletedAt()).isEqualTo(firstDeletion);
        assertThat(service.list(f.place(), 1, 20).getTotalElements()).isZero();
        assertThat(service.listMine(f.user(), 1, 20).totalElements()).isZero();
        assertThat(snapshot(f.review()).reviewMedia()).hasSize(2);
        assertThat(deletionRequests.findById(requestId).orElseThrow().getStatus()).isEqualTo(PlaceReviewDeletionRequestStatus.PENDING);
        assertThat(checkIns.findById(checkIn.getId()).orElseThrow().getStatus()).isEqualTo(LocationCheckInStatus.DWELL_VERIFIED);
        assertThat(sessions.findById(sessionId).orElseThrow().getCompletedCheckInId()).isEqualTo(checkIn.getId());
        var rewritten = service.create(f.user(), f.place(), new PlaceReviewCreateRequest(null, List.of(PlaceReviewRecommendReason.CLEAN), "다시 작성", null, null));
        assertThat(rewritten.reviewId()).isNotEqualTo(f.review());
        assertThat(service.listMine(f.user(), 1, 20).totalElements()).isEqualTo(1);
    }

    @Test
    void rejectsOtherOwnersPlacesAndAlreadyConnectedPhotosWithoutChangingReview() {
        Fixture f = fixture();
        assertCode(() -> service.delete(f.user() + 10000, f.place(), f.review()), MapErrorCode.PLACE_REVIEW_NOT_FOUND);
        assertCode(() -> service.delete(f.user(), f.place() + 10000, f.review()), MapErrorCode.PLACE_REVIEW_NOT_FOUND);
        Fixture other = fixture();
        assertCode(() -> service.update(f.user(), f.place(), f.review(), new PlaceReviewUpdateRequest(null, null, "변경", List.of(other.first()), null)), MapErrorCode.PLACE_REVIEW_MEDIA_FORBIDDEN);
        Long fresh = upload(f.user(), f.place());
        var secondReview = service.create(f.user(), f.place(), new PlaceReviewCreateRequest(null, List.of(PlaceReviewRecommendReason.CLEAN), "별도 리뷰", List.of(fresh), null));
        assertCode(() -> service.update(f.user(), f.place(), f.review(), new PlaceReviewUpdateRequest(null, null, "변경", List.of(fresh), null)), MapErrorCode.PLACE_REVIEW_MEDIA_ALREADY_CONNECTED);
        assertThat(snapshot(f.review()).content()).isEqualTo("원본");
        assertThat(snapshot(secondReview.reviewId()).reviewMedia()).hasSize(1);
    }

    @Test
    void competingCreateAndPatchConnectEachUploadOnlyOnceWithoutDeadlock() throws Exception {
        Fixture f = fixture();
        Long first = upload(f.user(), f.place());
        Long second = upload(f.user(), f.place());
        var start = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var create = pool.submit(() -> {
                start.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return competingResult(() -> service.create(f.user(), f.place(), new PlaceReviewCreateRequest(null,
                        List.of(PlaceReviewRecommendReason.CLEAN), "경쟁 작성", List.of(second, first), null)));
            });
            var patch = pool.submit(() -> {
                start.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return competingResult(() -> service.update(f.user(), f.place(), f.review(), new PlaceReviewUpdateRequest(null,
                        null, "경쟁 수정", List.of(first, second), null)));
            });
            assertThat(List.of(create.get(10, java.util.concurrent.TimeUnit.SECONDS), patch.get(10, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SUCCESS", "PLACE_REVIEW_MEDIA_ALREADY_CONNECTED");
        }
        assertThat(jdbc.queryForObject("select count(distinct review_id) from place_review_media_upload where place_review_media_upload_id in (?, ?)", Long.class, first, second)).isEqualTo(1L);
    }

    @Test
    void expiryCleanupPreservesConnectedMediaAndRemovesOnlyExpiredUploads() {
        Fixture f = fixture();
        Long unused = upload(f.user(), f.place());
        jdbc.update("update place_review_media_upload set expires_at=? where place_review_media_upload_id in (?, ?)", LocalDateTime.now(clock).minusHours(1), f.first(), unused);
        assertThat(mediaService.purgeExpiredUploads()).isGreaterThanOrEqualTo(1);
        assertThat(media.existsById(f.first())).isTrue();
        assertThat(media.existsById(unused)).isFalse();
        assertThat(snapshot(f.review()).reviewMedia()).hasSize(2);
        service.update(f.user(), f.place(), f.review(), new PlaceReviewUpdateRequest(null, null, null, List.of(f.second(), f.first()), null));
        assertThat(snapshot(f.review()).reviewMedia()).extracting(PlaceReviewMediaResponse::reviewMediaId).containsExactly(f.second(), f.first());
    }

    private String competingResult(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) throws Exception {
        try {
            operation.call();
            return "SUCCESS";
        } catch (MapException failure) {
            return ((MapErrorCode) failure.getErrorCode()).name();
        } catch (Exception failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new RuntimeException(failure);
        }
    }

    private Fixture fixture() {
        String id = UUID.randomUUID().toString();
        Long userId = users.saveAndFlush(User.builder().username(id).email(id + "@test.local").password("test-only").birthYear(2000).language("ko").country("KR").build()).getId();
        Long placeId = places.saveAndFlush(MapPlace.builder().name("리뷰 검증").address(id).latitude(35.18).longitude(128.1).registrant("admin").build()).getId();
        Long first = upload(userId, placeId);
        Long second = upload(userId, placeId);
        var review = service.create(userId, placeId, new PlaceReviewCreateRequest(null,
                List.of(PlaceReviewRecommendReason.FRIENDLY, PlaceReviewRecommendReason.CLEAN), "원본", List.of(first, second), null));
        return new Fixture(userId, placeId, review.reviewId(), first, second);
    }

    private Long upload(Long userId, Long placeId) {
        String key = "place-reviews/test/" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(clock);
        return media.saveAndFlush(PlaceReviewMediaUpload.upload(placeId, userId, key, "https://cdn.test/" + key, "image/jpeg", 10, now.plusDays(1), now)).getId();
    }

    private PlaceReviewResponse snapshot(Long reviewId) {
        return new TransactionTemplate(transactionManager).execute(status -> PlaceReviewResponse.from(reviews.findById(reviewId).orElseThrow()));
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, MapErrorCode expected) {
        assertThatThrownBy(action).isInstanceOf(MapException.class).extracting(e -> ((MapException) e).getErrorCode()).isEqualTo(expected);
    }

    private record Fixture(Long user, Long place, Long review, Long first, Long second) {}
}
