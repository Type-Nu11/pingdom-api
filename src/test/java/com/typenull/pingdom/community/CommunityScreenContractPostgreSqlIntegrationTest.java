package com.typenull.pingdom.community;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.community.application.CommunityCommentLikeService;
import com.typenull.pingdom.community.application.CommunityPostMediaService;
import com.typenull.pingdom.community.application.CommunityPostViewService;
import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostComment;
import com.typenull.pingdom.community.domain.CommunityPostMedia;
import com.typenull.pingdom.community.domain.CommunityPostMediaStatus;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostMediaRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 실제 PostgreSQL에서 확장 API·마이그레이션·잠금을 검증. S3 I/O만 mock하며 실제 버킷 접근은 검증하지 않음. */
@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false", "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.cloud.aws.s3.enabled=false",
        "management.health.redis.enabled=false", "fcm.enabled=false", "outbox.enabled=false",
        "community.media.cleanup-enabled=false", "pingdom.openapi.public-access.enabled=true"
})
@AutoConfigureMockMvc
class CommunityScreenContractPostgreSqlIntegrationTest {
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

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private CommunityPostRepository posts;
    @Autowired private CommunityPostCommentRepository comments;
    @Autowired private CommunityPostMediaRepository media;
    @Autowired private CommunityPostMediaService mediaService;
    @Autowired private CommunityPostViewService views;
    @Autowired private CommunityCommentLikeService likes;
    @Autowired private JwtTokenProvider tokens;
    @Autowired private Clock clock;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired private com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher deletion;
    @MockBean private S3ObjectStorage storage;
    private User author;
    private String bearer;

    @BeforeEach
    void prepare() {
        author = users.saveAndFlush(User.builder().username("screen-author").email("screen-author@example.com")
                .emailVerified(true).password("password").birthYear(1995).language("ko").country("한국").role(UserRole.USER).build());
        bearer = "Bearer " + tokens.generateAccessToken(author.getId(), author.getUsername(), author.getRole().name());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM community_post_media");
        jdbc.update("DELETE FROM community_comment_like");
        jdbc.update("DELETE FROM community_post_comment");
        jdbc.update("DELETE FROM community_post_daily_view");
        jdbc.update("DELETE FROM community_post_place");
        jdbc.update("DELETE FROM community_post_like");
        jdbc.update("DELETE FROM community_post");
        jdbc.update("DELETE FROM map_place");
        jdbc.update("DELETE FROM outbox_event");
        users.deleteAllInBatch();
    }

    @Test
    void keepsOldCreateRequestAndAddsFilteredListFields() throws Exception {
        JsonNode created = createPost("{\"categoryId\":\"TRAVEL\",\"title\":\"제목\",\"content\":\"본문 미리보기\"}");
        long postId = created.path("postId").asLong();
        mvc.perform(get("/community/posts").param("country", "KR").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.posts[0].preview").value("본문 미리보기"))
                .andExpect(jsonPath("$.posts[0].imageCount").value(0)).andExpect(jsonPath("$.posts[0].country").value("KR"))
                .andExpect(jsonPath("$.posts[0].category.categoryId").value("TRAVEL"))
                .andExpect(jsonPath("$.posts[0].likeCount").value(0)).andExpect(jsonPath("$.posts[0].commentCount").value(0))
                .andExpect(jsonPath("$.posts[0].viewCount").value(0)).andExpect(jsonPath("$.posts[0].thumbnailUrl").isEmpty());
        mvc.perform(get("/community/categories/TRAVEL/posts").param("country", "US").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
        mvc.perform(get("/community/posts").param("country", "INVALID").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_COUNTRY"));
        mvc.perform(get("/community/posts/{id}", postId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.images").isEmpty()).andExpect(jsonPath("$.viewCount").value(0));
        mvc.perform(get("/community/countries").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void uploadsImageAndPreservesImageOrderWithAccurateCounts() throws Exception {
        AtomicInteger sequence = new AtomicInteger();
        when(storage.put(any(byte[].class), anyString(), anyString(), eq("community-posts"))).thenAnswer(call -> {
            int number = sequence.incrementAndGet();
            return new S3ObjectStorage.S3PutResult("community-posts/" + number, "https://example.com/" + number + ".png");
        });
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        List<Long> imageIds = new ArrayList<>();
        for (int number = 0; number < 2; number++) {
            String body = mvc.perform(multipart("/community/images")
                            .file(new MockMultipartFile("file", "photo.png", "image/png", bytes.toByteArray()))
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.contentType").value("image/png"))
                    .andExpect(jsonPath("$.expiresAt").exists()).andReturn().getResponse().getContentAsString();
            imageIds.add(mapper.readTree(body).path("imageId").asLong());
        }
        java.util.Collections.reverse(imageIds);
        long postId = createPost(mapper.writeValueAsString(java.util.Map.of("categoryId", "TRAVEL", "title", "사진", "content", "본문", "imageIds", imageIds)))
                .path("postId").asLong();
        mvc.perform(get("/community/posts/{id}", postId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.images[0].imageId").value(imageIds.getFirst()))
                .andExpect(jsonPath("$.images[0].displayOrder").value(0));
        mvc.perform(post("/community/posts/{id}/likes", postId).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
        comments.saveAndFlush(CommunityPostComment.create(posts.findById(postId).orElseThrow(), author.getId(), "댓글"));
        mvc.perform(get("/community/posts").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].imageCount").value(2)).andExpect(jsonPath("$.posts[0].likeCount").value(1))
                .andExpect(jsonPath("$.posts[0].commentCount").value(1)).andExpect(jsonPath("$.posts[0].thumbnailUrl").value("https://example.com/2.png"));
        mvc.perform(delete("/community/images/{id}", imageIds.getFirst()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsForeignExpiredReusedAndTooManyImagesWithoutSavingPost() throws Exception {
        CommunityPostMedia owned = temporary(author.getId(), 0);
        CommunityPostMedia foreign = temporary(author.getId() + 1000, 0);
        mvc.perform(post("/community/posts").header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(postWithImages(List.of(owned.getId(), foreign.getId()))))
                .andExpect(status().isForbidden());
        assertThat(posts.count()).isZero();
        assertThat(media.findById(owned.getId()).orElseThrow().getStatus()).isEqualTo(CommunityPostMediaStatus.UPLOADED);
        CommunityPostMedia expired = temporary(author.getId(), -24);
        mvc.perform(post("/community/posts").header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(postWithImages(List.of(expired.getId())))).andExpect(status().isGone());
        mvc.perform(post("/community/posts").header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(postWithImages(List.of(owned.getId(), owned.getId())))).andExpect(status().isBadRequest());
        mvc.perform(post("/community/posts").header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(postWithImages(java.util.stream.LongStream.rangeClosed(1, 11).boxed().toList())))
                .andExpect(status().isBadRequest());
        createPost(postWithImages(List.of(owned.getId())));
        mvc.perform(post("/community/posts").header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(postWithImages(List.of(owned.getId())))).andExpect(status().isConflict());
        assertThat(posts.count()).isEqualTo(1);
    }

    @Test
    void supportsTenImagesAndNullablePlaceMetadata() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) { ids.add(temporary(author.getId(), 0).getId()); }
        long postId = createPost(postWithImages(ids)).path("postId").asLong();
        mvc.perform(get("/community/posts/{id}", postId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.images.length()").value(10))
                .andExpect(jsonPath("$.images[9].displayOrder").value(9)).andExpect(jsonPath("$.region").isEmpty());
        long placeId = jdbc.queryForObject("INSERT INTO map_place (place_name,address,latitude,longitude,registrant,photo_count) VALUES ('장소','서울',37,127,'test',0) RETURNING map_place_id", Long.class);
        jdbc.update("INSERT INTO community_post_place (community_post_id,map_place_id) VALUES (?,?)", postId, placeId);
        mvc.perform(get("/community/posts/{id}", postId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.places[0].deleted").value(false))
                .andExpect(jsonPath("$.places[0].imageUrl").isEmpty()).andExpect(jsonPath("$.places[0].category").isEmpty())
                .andExpect(jsonPath("$.region").value("서울"));
    }

    @Test
    void connectsRepliesAndLikesAndExcludesHiddenParentBranch() throws Exception {
        CommunityPost post = posts.saveAndFlush(CommunityPost.create("TRAVEL", "글", "본문", author.getId()));
        CommunityPostComment root = comments.saveAndFlush(CommunityPostComment.create(post, author.getId(), "부모"));
        String response = mvc.perform(post("/community/posts/{id}/comments", post.getId()).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"답글\",\"parentCommentId\":" + root.getId() + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long replyId = mapper.readTree(response).path("commentId").asLong();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/community/posts/{id}/comments/{commentId}/likes", post.getId(), replyId).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.likeCount").value(1)).andExpect(jsonPath("$.liked").value(true));
        }
        mvc.perform(get("/community/posts/{id}/comments/{commentId}/replies", post.getId(), root.getId()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comments[0].parentCommentId").value(root.getId()))
                .andExpect(jsonPath("$.comments[0].likeCount").value(1)).andExpect(jsonPath("$.comments[0].liked").value(true));
        mvc.perform(get("/community/posts/{id}/comments", post.getId()).param("rootOnly", "true").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1));
        mvc.perform(post("/community/posts/{id}/comments", post.getId()).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"잘못된 답글\",\"parentCommentId\":" + replyId + "}"))
                .andExpect(status().isBadRequest());
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete("/community/posts/{id}/comments/{commentId}/likes", post.getId(), replyId).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.likeCount").value(0));
        }
        jdbc.update("UPDATE community_post_comment SET hidden = TRUE, hidden_at = CURRENT_TIMESTAMP, hidden_by_admin_user_id = ? WHERE community_post_comment_id = ?", author.getId(), root.getId());
        mvc.perform(get("/community/posts/{id}/comments", post.getId()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
        mvc.perform(get("/community/posts").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.posts[0].commentCount").value(0));
        mvc.perform(post("/community/posts/{id}/comments/{commentId}/likes", post.getId(), replyId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void preventsParentFromAnotherPostAndHidesWithdrawnCountry() throws Exception {
        CommunityPost first = posts.saveAndFlush(CommunityPost.create("TRAVEL", "첫 글", "본문", author.getId()));
        CommunityPost second = posts.saveAndFlush(CommunityPost.create("TRAVEL", "다른 글", "본문", author.getId()));
        CommunityPostComment parent = comments.saveAndFlush(CommunityPostComment.create(first, author.getId(), "부모"));
        mvc.perform(post("/community/posts/{id}/comments", second.getId()).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"답글\",\"parentCommentId\":" + parent.getId() + "}"))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE users SET withdrawn_at = CURRENT_TIMESTAMP WHERE id = ?", author.getId());
        assertThat(posts.findListItems(null, true, List.of("kr", "한국"), org.springframework.data.domain.PageRequest.of(0, 20))).isEmpty();
    }

    @Test
    void concurrentViewsAndLikesAreIdempotent() throws Exception {
        CommunityPost post = posts.saveAndFlush(CommunityPost.create("TRAVEL", "글", "본문", author.getId()));
        CommunityPostComment comment = comments.saveAndFlush(CommunityPostComment.create(post, author.getId(), "댓글"));
        try (ExecutorService executor = Executors.newFixedThreadPool(6)) {
            List<Callable<Void>> actions = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                actions.add(() -> { views.record(post.getId(), author.getId()); return null; });
                actions.add(() -> { likes.like(post.getId(), comment.getId(), author.getId()); return null; });
            }
            for (Future<Void> result : executor.invokeAll(actions, 30, TimeUnit.SECONDS)) { result.get(); }
        }
        assertThat(posts.findById(post.getId()).orElseThrow().getViewCount()).isEqualTo(1);
        assertThat(likes.find(post.getId(), comment.getId(), author.getId()).likeCount()).isEqualTo(1);
    }

    @Test
    void expiryAndCancellationLeaveConnectedImagesIntact() {
        CommunityPost post = posts.saveAndFlush(CommunityPost.create("TRAVEL", "글", "본문", author.getId()));
        CommunityPostMedia connected = CommunityPostMedia.upload(author.getId(), "connected", "https://example.com/connected", "image/png", 1,
                LocalDateTime.now(clock).minusDays(2));
        connected.connect(post, 0, LocalDateTime.now(clock).minusDays(2));
        connected = media.saveAndFlush(connected);
        CommunityPostMedia expired = temporary(author.getId(), -25);
        assertThat(mediaService.purgeExpiredUploads()).isEqualTo(1);
        assertThat(media.existsById(expired.getId())).isFalse();
        assertThat(media.existsById(connected.getId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE event_type = 'S3_OBJECT_DELETE_REQUESTED'", Long.class)).isEqualTo(1);
        CommunityPostMedia cancelled = temporary(author.getId(), 0);
        mediaService.cancel(author.getId(), cancelled.getId());
        assertThat(media.existsById(cancelled.getId())).isFalse();
        verify(storage, never()).delete(anyString());
    }

    @Test
    void rejectsBadUploadAndUnauthenticatedNewEndpoints() throws Exception {
        mvc.perform(multipart("/community/images").file(new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1}))
                        .header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/community/images").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/community/posts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/community/posts/1/view")).andExpect(status().isUnauthorized());
        verifyNoInteractions(storage);
    }

    @Test
    void documentsAllNewContractsInAppOpenApi() throws Exception {
        String body = mvc.perform(get("/v3/api-docs/app")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode spec = mapper.readTree(body);
        assertThat(spec.at("/paths/~1community~1posts/get").isMissingNode()).isFalse();
        assertThat(spec.at("/paths/~1community~1images/post/responses/201").isMissingNode()).isFalse();
        assertThat(spec.at("/paths/~1community~1images/post/requestBody/content/multipart~1form-data/schema/properties/file/format").asText()).isEqualTo("binary");
        assertThat(spec.at("/paths/~1community~1posts~1{postId}~1comments~1{commentId}~1likes/post").isMissingNode()).isFalse();
        assertThat(spec.at("/paths/~1community~1posts~1{postId}~1comments~1{commentId}~1likes/post/responses/200/content").toString())
                .contains("\"$ref\":\"#/components/schemas/CommunityCommentLikeResponse\"");
        assertThat(spec.at("/paths/~1community~1posts~1{postId}~1view/post/responses/200/content").toString())
                .contains("\"$ref\":\"#/components/schemas/CommunityPostViewResponse\"");
        assertThat(spec.at("/components/schemas/CommunityPostSummary/properties/commentCount").isMissingNode()).isFalse();
        assertThat(spec.at("/components/schemas/CommunityCommentSummary/properties/parentCommentId").isMissingNode()).isFalse();
        assertThat(spec.at("/components/schemas/CommunityPostCreateRequest/properties/imageIds/maxItems").asInt()).isEqualTo(10);
    }

    @Test
    void cleanupSkipsImagesLockedByConnectionAcrossExpiryBoundary() throws Exception {
        CommunityPost post = posts.saveAndFlush(CommunityPost.create("TRAVEL", "글", "본문", author.getId()));
        CommunityPostMedia pending = temporary(author.getId(), -23);
        CountDownLatch connectedButUncommitted = new CountDownLatch(1);
        CountDownLatch releaseCommit = new CountDownLatch(1);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        Clock cleanupClock = Clock.fixed(clock.instant().plus(java.time.Duration.ofHours(2)), clock.getZone());
        var cleaner = new CommunityPostMediaService(media, mock(com.typenull.pingdom.post.infrastructure.storage.image.ImageUploadProcessor.class),
                storage, deletion, cleanupClock, transactionManager);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> connection = executor.submit(() -> transaction.executeWithoutResult(status -> {
                mediaService.connect(author.getId(), post, List.of(pending.getId()));
                connectedButUncommitted.countDown();
                try {
                    if (!releaseCommit.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("commit release timeout"); }
                } catch (InterruptedException exception) { throw new IllegalStateException(exception); }
            }));
            try {
                assertThat(connectedButUncommitted.await(10, TimeUnit.SECONDS)).isTrue();
                Future<Integer> cleanup = executor.submit(() -> transaction.execute(status -> cleaner.purgeExpiredUploads()));
                assertThat(cleanup.get(5, TimeUnit.SECONDS)).isZero();
            } finally {
                releaseCommit.countDown();
            }
            connection.get(10, TimeUnit.SECONDS);
        }
        assertThat(media.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(CommunityPostMediaStatus.CONNECTED);
        Integer remaining = transaction.execute(status -> cleaner.purgeExpiredUploads());
        assertThat(remaining).isZero();
    }

    @Test
    void preservesRequestedFirstPlaceAndUsesAdministrativeRegion() throws Exception {
        jdbc.update("INSERT INTO place_administrative_region (region_code,sido,sigungu,region_name,updated_at) VALUES ('11140','서울','중구','서울 중구',CURRENT_TIMESTAMP)");
        long first = jdbc.queryForObject("INSERT INTO map_place (place_name,address,latitude,longitude,registrant,photo_count) VALUES ('첫 장소','부산',35,129,'test',0) RETURNING map_place_id", Long.class);
        long second = jdbc.queryForObject("INSERT INTO map_place (place_name,address,latitude,longitude,registrant,photo_count,region_code,image_url,category) VALUES ('둘째 장소','서울 주소',37,127,'test',0,'11140','https://example.com/place.png','카페') RETURNING map_place_id", Long.class);
        long postId = createPost(mapper.writeValueAsString(java.util.Map.of("categoryId", "TRAVEL", "title", "글", "content", "본문", "placeIds", List.of(second, first))))
                .path("postId").asLong();
        mvc.perform(get("/community/posts/{id}", postId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.region").value("서울 중구"))
                .andExpect(jsonPath("$.places[0].placeId").value(second)).andExpect(jsonPath("$.places[0].category").value("카페"))
                .andExpect(jsonPath("$.places[0].imageUrl").value("https://example.com/place.png"));
        mvc.perform(get("/community/posts").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.posts[0].region").value("서울 중구"));
        jdbc.update("DELETE FROM place_administrative_region WHERE region_code = '11140'");
    }

    private JsonNode createPost(String json) throws Exception {
        String body = mvc.perform(post("/community/posts").header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private String postWithImages(List<Long> ids) throws Exception {
        return mapper.writeValueAsString(java.util.Map.of("categoryId", "TRAVEL", "title", "사진", "content", "본문", "imageIds", ids));
    }

    private CommunityPostMedia temporary(long userId, int hours) {
        String key = java.util.UUID.randomUUID().toString();
        return media.saveAndFlush(CommunityPostMedia.upload(userId, key, "https://example.com/" + key, "image/png", 1,
                LocalDateTime.now(clock).plusHours(hours)));
    }
}
