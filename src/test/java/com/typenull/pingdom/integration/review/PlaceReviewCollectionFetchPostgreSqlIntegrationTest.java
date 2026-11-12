package com.typenull.pingdom.integration.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.place.api.dto.review.PlaceReviewMediaResponse;
import com.typenull.pingdom.place.application.service.review.MerchantPlaceReviewModerationService;
import com.typenull.pingdom.place.application.service.review.PlaceReviewService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequest;
import com.typenull.pingdom.place.domain.review.PlaceReviewMediaUpload;
import com.typenull.pingdom.place.domain.review.PlaceReviewRecommendReason;
import com.typenull.pingdom.place.domain.review.PlaceReviewVisibilityStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true",
        "spring.cloud.aws.s3.enabled=false", "management.health.redis.enabled=false",
        "place.review-media.cleanup-enabled=false", "fcm.enabled=false", "outbox.enabled=false"
})
@Import(PlaceReviewCollectionFetchPostgreSqlIntegrationTest.SqlConfiguration.class)
class PlaceReviewCollectionFetchPostgreSqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("pingdom").withUsername("pingdom").withPassword("pingdom");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private PlaceReviewService reviewService;
    @Autowired private MerchantPlaceReviewModerationService merchantReviewService;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext private EntityManager entityManager;

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 100})
    void batchesEachCollectionForPublicOwnAndMerchantPages(int size) {
        Fixture fixture = fixture(size);

        // fixture는 먼저 커밋하며 각 서비스 호출은 별도 영속성 컨텍스트에서 시작한다.
        var publicPage = SqlRecorder.record(() -> reviewService.list(fixture.placeId(), 1, size));
        assertThat(publicPage.value().getTotalElements()).isEqualTo(size);
        assertThat(publicPage.value().getContent()).hasSize(size);
        assertCollectionQueries(publicPage.statements());
        for (int i = 0; i < size; i++) {
            var review = publicPage.value().getContent().get(i);
            assertCollections(review.reviewId(), review.recommendReason(), review.recommendReasons(),
                    review.imageUrls(), review.reviewMedia(), fixture.reviews().get(i));
        }

        var ownPage = SqlRecorder.record(() -> reviewService.listMine(fixture.userId(), 1, size));
        assertThat(ownPage.value().totalElements()).isEqualTo(size);
        assertThat(ownPage.value().reviews()).hasSize(size);
        assertCollectionQueries(ownPage.statements());
        for (int i = 0; i < size; i++) {
            var review = ownPage.value().reviews().get(i);
            assertCollections(review.reviewId(), review.recommendReason(), review.recommendReasons(),
                    review.imageUrls(), review.reviewMedia(), fixture.reviews().get(i));
        }

        var merchantPage = SqlRecorder.record(() -> merchantReviewService.list(fixture.userId(), fixture.placeId(), 1, size));
        assertThat(merchantPage.value().totalElements()).isEqualTo(size);
        assertThat(merchantPage.value().reviews()).hasSize(size);
        assertCollectionQueries(merchantPage.statements());
        for (int i = 0; i < size; i++) {
            var review = merchantPage.value().reviews().get(i);
            assertCollections(review.reviewId(), review.recommendReason(), review.recommendReasons(),
                    review.imageUrls(), review.reviewMedia(), fixture.reviews().get(i));
        }
    }

    @Test
    void keepsVisibilityFiltersStablePagesEmptyPhotosAndDeletionRequest() {
        Fixture fixture = fixture(5);
        Long deletedId = fixture.reviews().get(1).id();
        Long hiddenId = fixture.reviews().get(3).id();
        Long requestId = new TransactionTemplate(transactionManager).execute(status -> {
            entityManager.find(PlaceReview.class, deletedId).markDeleted(NOW.plusMinutes(1));
            PlaceReview hidden = entityManager.find(PlaceReview.class, hiddenId);
            hidden.hide(NOW.plusMinutes(1));
            PlaceReviewDeletionRequest request = PlaceReviewDeletionRequest.submit(hidden, fixture.userId(), "심사 요청", NOW.plusMinutes(1));
            entityManager.persist(request);
            return request.getId();
        });

        var firstPage = reviewService.list(fixture.placeId(), 1, 2);
        assertThat(firstPage.getContent()).extracting(review -> review.reviewId())
                .containsExactly(fixture.reviews().get(0).id(), fixture.reviews().get(2).id());
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();
        var lastPage = reviewService.list(fixture.placeId(), 2, 2);
        assertThat(lastPage.getContent()).extracting(review -> review.reviewId()).containsExactly(fixture.reviews().get(4).id());
        assertThat(lastPage.hasNext()).isFalse();
        assertThat(lastPage.getContent().getFirst().imageUrls()).isEmpty();
        assertThat(lastPage.getContent().getFirst().reviewMedia()).isEmpty();

        var ownPage = reviewService.listMine(fixture.userId(), 1, 100);
        assertThat(ownPage.totalElements()).isEqualTo(4);
        assertThat(ownPage.reviews()).extracting(review -> review.reviewId()).containsExactly(
                fixture.reviews().get(0).id(), fixture.reviews().get(2).id(), hiddenId, fixture.reviews().get(4).id());
        assertThat(ownPage.reviews().get(2).visibilityStatus()).isEqualTo(PlaceReviewVisibilityStatus.HIDDEN);

        var merchantPage = merchantReviewService.list(fixture.userId(), fixture.placeId(), 1, 100);
        assertThat(merchantPage.totalElements()).isEqualTo(5);
        assertThat(merchantPage.reviews()).extracting(review -> review.reviewId())
                .containsExactlyElementsOf(fixture.reviews().stream().map(ExpectedReview::id).toList());
        assertThat(merchantPage.reviews().get(1).visibilityStatus()).isEqualTo(PlaceReviewVisibilityStatus.DELETED);
        assertThat(merchantPage.reviews().get(3).deletionRequest().deletionRequestId()).isEqualTo(requestId);
        assertThat(merchantPage.reviews().getFirst().deletionRequest()).isNull();
    }

    private void assertCollectionQueries(List<String> statements) {
        for (String table : List.of("place_review_recommend_reason", "place_review_image", "place_review_media_upload")) {
            assertThat(queriesFrom(statements, table)).as("%s의 SELECT는 페이지 내 리뷰 수와 무관하게 한 번", table).hasSize(1);
        }
        assertThat(queriesFrom(statements, "place_review")).anySatisfy(sql ->
                assertThat(sql).contains("order by").contains("fetch first"));
    }

    private List<String> queriesFrom(List<String> statements, String table) {
        Pattern fromTable = Pattern.compile("\\bfrom\\s+" + table + "\\b");
        return statements.stream()
                .map(sql -> sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " "))
                .filter(sql -> fromTable.matcher(sql).find())
                .toList();
    }

    private void assertCollections(Long id, String legacyReason, List<PlaceReviewRecommendReason> reasons,
            List<String> urls, List<PlaceReviewMediaResponse> media, ExpectedReview expected) {
        assertThat(id).isEqualTo(expected.id());
        assertThat(legacyReason).isEqualTo(expected.legacyReason());
        assertThat(reasons).containsExactlyElementsOf(expected.reasons());
        // legacy URL은 순서 컬럼이 없는 bag이므로 내용만 비교한다. 연결 미디어의 명시된 순서는 별도로 확인한다.
        assertThat(urls).containsExactlyInAnyOrderElementsOf(expected.urls());
        assertThat(media).extracting(PlaceReviewMediaResponse::reviewMediaId).containsExactlyElementsOf(expected.mediaIds());
        assertThat(media).extracting(PlaceReviewMediaResponse::imageUrl).containsExactlyElementsOf(expected.urls());
    }

    private Fixture fixture(int size) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String key = UUID.randomUUID().toString();
            User user = User.builder().username("review-" + key).email(key + "@test.local").password("test-only")
                    .birthYear(2000).language("ko").country("KR").build();
            entityManager.persist(user);
            MapPlace place = MapPlace.builder().name("리뷰 배치 조회").address(key).latitude(35.18).longitude(128.1)
                    .registrant("admin").build();
            entityManager.persist(place);
            MerchantOwnerProfile owner = MerchantOwnerProfile.pending(user.getId(), "상점", "상점주", null,
                    user.getEmail(), "01012345678", NOW);
            owner.approve(user.getId(), NOW);
            entityManager.persist(owner);
            entityManager.persist(MerchantOwnerPlace.builder().placeId(place.getId()).merchantOwnerUserId(user.getId()).createdAt(NOW).build());

            List<ExpectedReview> expected = new ArrayList<>();
            for (int index = 0; index < size; index++) {
                List<PlaceReviewRecommendReason> reasons = index == 0 ? List.of()
                        : List.of(PlaceReviewRecommendReason.CLEAN, PlaceReviewRecommendReason.FRIENDLY);
                String legacyReason = reasons.isEmpty() ? "기존 추천 사유" : reasons.getFirst().name();
                PlaceReview review = PlaceReview.create(place, user.getId(), legacyReason, reasons, "리뷰 " + index, List.of(), NOW);
                entityManager.persist(review);
                int photoCount = index == 0 ? 0 : 3;
                int reviewIndex = index;
                List<String> urls = IntStream.range(0, photoCount)
                        .mapToObj(order -> "https://cdn.test/" + key + "/" + reviewIndex + "/" + order).toList();
                Long[] mediaIds = new Long[photoCount];
                // INSERT 순서를 뒤집어 ID 순서가 아닌 displayOrder에 따른 응답인지 검증한다.
                for (int order = photoCount - 1; order >= 0; order--) {
                    PlaceReviewMediaUpload upload = PlaceReviewMediaUpload.upload(place.getId(), user.getId(),
                            key + "/" + index + "/" + order, urls.get(order), "image/jpeg", 10, NOW.plusDays(1), NOW);
                    upload.connect(review, order, NOW);
                    entityManager.persist(upload);
                    review.addMediaUpload(upload);
                    mediaIds[order] = upload.getId();
                }
                expected.add(new ExpectedReview(review.getId(), legacyReason, reasons, urls, Arrays.asList(mediaIds)));
            }
            Collections.reverse(expected);
            return new Fixture(user.getId(), place.getId(), List.copyOf(expected));
        });
    }

    private record Fixture(Long userId, Long placeId, List<ExpectedReview> reviews) {}
    private record ExpectedReview(Long id, String legacyReason, List<PlaceReviewRecommendReason> reasons,
            List<String> urls, List<Long> mediaIds) {}
    private record Measured<T>(T value, List<String> statements) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfiguration {
        @Bean
        HibernatePropertiesCustomizer reviewCollectionSqlRecorder() {
            return properties -> properties.put(AvailableSettings.STATEMENT_INSPECTOR, new SqlRecorder());
        }
    }

    static class SqlRecorder implements StatementInspector {
        private static final ThreadLocal<List<String>> statements = new ThreadLocal<>();

        @Override
        public String inspect(String sql) {
            List<String> current = statements.get();
            if (current != null) current.add(sql);
            return sql;
        }

        static <T> Measured<T> record(Supplier<T> operation) {
            List<String> captured = new ArrayList<>();
            statements.set(captured);
            try {
                return new Measured<>(operation.get(), List.copyOf(captured));
            } finally {
                statements.remove();
            }
        }
    }
}
