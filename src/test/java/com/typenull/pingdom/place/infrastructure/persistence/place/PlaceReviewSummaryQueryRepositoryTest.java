package com.typenull.pingdom.place.infrastructure.persistence.place;

import static com.typenull.pingdom.place.domain.review.PlaceReviewRecommendReason.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Spring Context 없이 최소 H2 fixture에서 운영 집계 SQL 자체를 실행합니다. PostgreSQL 실행 검증은 별도입니다. */
class PlaceReviewSummaryQueryRepositoryTest {

    private JdbcTemplate jdbc;
    private PlaceReviewSummaryQueryRepository repository;
    private SingleConnectionDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "", true);
        jdbc = new JdbcTemplate(dataSource);
        repository = new PlaceReviewSummaryQueryRepository(new NamedParameterJdbcTemplate(dataSource));
        jdbc.execute("CREATE TABLE map_place (map_place_id BIGINT PRIMARY KEY)");
        jdbc.execute("""
                CREATE TABLE place_review (
                    id BIGINT PRIMARY KEY, place_id BIGINT, user_id BIGINT,
                    recommend_reason VARCHAR(100), visibility_status VARCHAR(20)
                )
                """);
        jdbc.execute("CREATE TABLE place_review_recommend_reason (review_id BIGINT, reason VARCHAR(50))");
        jdbc.update("INSERT INTO map_place VALUES (17), (18)");
    }

    @org.junit.jupiter.api.AfterEach
    void closeConnection() {
        dataSource.destroy();
    }

    @Test
    void countsDistinctAuthorsAcrossMultipleReviewsAndRepeatedTags() {
        review(1, 17, 10L, "PARKING", "VISIBLE");
        reasons(1, "FRIENDLY", "CLEAN", "FRIENDLY");
        review(2, 17, 10L, "GOOD_FOOD", "VISIBLE");
        reasons(2, "FRIENDLY", "GOOD_FOOD");
        review(3, 17, 20L, "PHOTO_SPOT", "VISIBLE");

        var result = repository.findByPlaceId(17L).orElseThrow();

        assertThat(result.totalReviewCount()).isEqualTo(3);
        assertThat(result.participantCount()).isEqualTo(2);
        assertThat(result.reasonCounts()).containsEntry(FRIENDLY, 1L).containsEntry(CLEAN, 1L)
                .containsEntry(GOOD_FOOD, 1L).containsEntry(PHOTO_SPOT, 1L).containsEntry(PARKING, 0L);
    }

    @Test
    void usesLegacyOnlyWhenStructuredCollectionIsEmptyAndIgnoresUnknownCodes() {
        review(1, 17, 10L, "FRIENDLY", "VISIBLE");
        review(2, 17, 20L, "FRIENDLY", "VISIBLE");
        reasons(2, "UNKNOWN");
        review(3, 17, 30L, "unknown", "VISIBLE");
        review(4, 17, 40L, "friendly", "VISIBLE");
        review(5, 17, 50L, " CLEAN ", "VISIBLE");
        review(6, 17, 60L, "", "VISIBLE");

        var result = repository.findByPlaceId(17L).orElseThrow();

        assertThat(result.totalReviewCount()).isEqualTo(6);
        assertThat(result.participantCount()).isEqualTo(1);
        assertThat(result.reasonCounts()).containsEntry(FRIENDLY, 1L);
        assertThat(result.reasonCounts().values()).filteredOn(count -> count != 0).containsExactly(1L);
    }

    @Test
    void excludesHiddenDeletedAndOtherPlaceReviews() {
        review(1, 17, 10L, "FRIENDLY", "VISIBLE");
        review(2, 17, 20L, "GOOD_FOOD", "HIDDEN");
        reasons(2, "CLEAN");
        review(3, 17, 30L, "GOOD_FOOD", "DELETED");
        review(4, 18, 40L, "GOOD_FOOD", "VISIBLE");

        var result = repository.findByPlaceId(17L).orElseThrow();

        assertThat(result.totalReviewCount()).isEqualTo(1);
        assertThat(result.participantCount()).isEqualTo(1);
        assertThat(result.reasonCounts()).containsEntry(FRIENDLY, 1L).containsEntry(CLEAN, 0L).containsEntry(GOOD_FOOD, 0L);
    }

    @Test
    void distinguishesEmptyExistingPlaceFromMissingPlace() {
        var result = repository.findByPlaceId(17L).orElseThrow();

        assertThat(result.totalReviewCount()).isZero();
        assertThat(result.participantCount()).isZero();
        assertThat(result.reasonCounts()).hasSize(7);
        assertThat(result.reasonCounts().values()).containsOnly(0L);
        assertThat(repository.findByPlaceId(404L)).isEmpty();
        assertThat(repository.findByPlaceId(0L)).isEmpty();
        assertThat(repository.findByPlaceId(-1L)).isEmpty();
    }

    @Test
    void aggregatesBeyondOneReviewPage() {
        for (long id = 1; id <= 25; id++) {
            review(id, 17, id, "PHOTO_SPOT", "VISIBLE");
        }

        var result = repository.findByPlaceId(17L).orElseThrow();

        assertThat(result.totalReviewCount()).isEqualTo(25);
        assertThat(result.participantCount()).isEqualTo(25);
        assertThat(result.reasonCounts()).containsEntry(PHOTO_SPOT, 25L);
    }

    @Test
    void rereadsAfterTagEditDeletionHidingAndRestoration() {
        review(1, 17, 10L, "FRIENDLY", "VISIBLE");
        reasons(1, "FRIENDLY");
        review(2, 17, 20L, "PHOTO_SPOT", "VISIBLE");
        assertThat(repository.findByPlaceId(17L).orElseThrow().participantCount()).isEqualTo(2);

        jdbc.update("UPDATE place_review_recommend_reason SET reason = 'CLEAN' WHERE review_id = 1");
        jdbc.update("UPDATE place_review SET visibility_status = 'DELETED' WHERE id = 2");
        var edited = repository.findByPlaceId(17L).orElseThrow();
        assertThat(edited.totalReviewCount()).isEqualTo(1);
        assertThat(edited.participantCount()).isEqualTo(1);
        assertThat(edited.reasonCounts()).containsEntry(FRIENDLY, 0L).containsEntry(CLEAN, 1L).containsEntry(PHOTO_SPOT, 0L);

        jdbc.update("UPDATE place_review SET visibility_status = 'HIDDEN' WHERE id = 1");
        assertThat(repository.findByPlaceId(17L).orElseThrow().participantCount()).isZero();
        jdbc.update("UPDATE place_review SET visibility_status = 'VISIBLE' WHERE id = 1");
        assertThat(repository.findByPlaceId(17L).orElseThrow().reasonCounts()).containsEntry(CLEAN, 1L);
    }

    @Test
    void includesAuthorlessReviewsOnlyInReviewCount() {
        review(1, 17, null, "FRIENDLY", "VISIBLE");
        review(2, 17, null, "PHOTO_SPOT", "VISIBLE");
        reasons(2, "CLEAN");

        var result = repository.findByPlaceId(17L).orElseThrow();

        assertThat(result.totalReviewCount()).isEqualTo(2);
        assertThat(result.participantCount()).isZero();
        assertThat(result.reasonCounts().values()).containsOnly(0L);
    }

    private void review(long id, long placeId, Long userId, String legacyReason, String visibility) {
        jdbc.update("INSERT INTO place_review VALUES (?, ?, ?, ?, ?)", id, placeId, userId, legacyReason, visibility);
    }

    private void reasons(long reviewId, String... values) {
        for (String value : values) {
            jdbc.update("INSERT INTO place_review_recommend_reason VALUES (?, ?)", reviewId, value);
        }
    }
}
