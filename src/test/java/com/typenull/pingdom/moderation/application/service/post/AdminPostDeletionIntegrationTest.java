package com.typenull.pingdom.moderation.application.service.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-post-deletion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.docker.compose.enabled=false", "spring.cloud.aws.s3.enabled=false",
        "outbox.enabled=false", "fcm.enabled=false"
})
class AdminPostDeletionIntegrationTest {

    @Autowired private AdminPostServiceImpl service;
    @Autowired private MapImageRepository images;
    @Autowired private MapPlaceRepository places;
    @Autowired private PostReportRepository reports;
    @Autowired private UserRepository users;
    @Autowired private OutboxEventRepository outbox;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @SpyBean private AdminAuditLogRepository audit;
    @MockBean private AdminRoleAuthorizationService authorization;

    @BeforeEach
    void clean() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            audit.deleteAllInBatch();
            outbox.deleteAllInBatch();
            reports.deleteAllInBatch();
            images.deleteAllInBatch();
            places.deleteAllInBatch();
            users.deleteAllInBatch();
        });
    }

    @Test
    void hiddenDeletionKeepsOtherActivePhotoAndDetachesReportsWithAuditOutbox() {
        Fixture fixture = fixture();
        service.hidePost(fixture.imageId(), "hide", fixture.userId());

        service.deletePost(fixture.imageId(), fixture.userId());

        assertRemainingPhotoCount(fixture);
        assertThat(reports.findById(fixture.reportId()).orElseThrow().getMapImage()).isNull();
        assertThat(outbox.count()).isEqualTo(2L);
        assertThat(audit.findAll()).extracting(log -> log.getAction())
                .containsExactlyInAnyOrder(AdminAuditAction.POST_HIDDEN, AdminAuditAction.POST_DELETED);
    }

    @Test
    void activeDeletionDecrementsOnce() {
        Fixture fixture = fixture();

        service.deletePost(fixture.imageId(), fixture.userId());

        assertRemainingPhotoCount(fixture);
        assertThat(outbox.count()).isEqualTo(2L);
        assertThat(audit.count()).isEqualTo(1L);
    }

    @Test
    void restoreThenDeletionRetainsOtherActivePhoto() {
        Fixture fixture = fixture();
        service.hidePost(fixture.imageId(), "hide", fixture.userId());
        service.restorePost(fixture.imageId(), "restore", fixture.userId());

        service.deletePost(fixture.imageId(), fixture.userId());

        assertRemainingPhotoCount(fixture);
    }

    @Test
    void auditFailureRollsBackPhotoCountReportDetachAndOutbox() {
        Fixture fixture = fixture();
        doThrow(new DataIntegrityViolationException("audit failed"))
                .when(audit).save(argThat(log -> log.getAction() == AdminAuditAction.POST_DELETED));

        assertThatThrownBy(() -> service.deletePost(fixture.imageId(), fixture.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(images.existsById(fixture.imageId())).isTrue();
        assertThat(places.findById(fixture.placeId()).orElseThrow().currentPhotoCount()).isEqualTo(2L);
        assertThat(reports.findById(fixture.reportId()).orElseThrow().getMapImage().getId())
                .isEqualTo(fixture.imageId());
        assertThat(outbox.count()).isZero();
        assertThat(audit.count()).isZero();
    }

    @Test
    void bulkClearDiscardsPreloadedPhotoBeforeReadingLatestVisibility() {
        Fixture fixture = fixture();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            MapImage preloaded = images.findById(fixture.imageId()).orElseThrow();
            assertThat(preloaded.isVisible()).isTrue();
            jdbc.update("UPDATE map_image SET visibility_status = 'AUTO_HIDDEN' WHERE map_image_id = ?",
                    fixture.imageId());
            jdbc.update("UPDATE map_place SET photo_count = 1 WHERE map_place_id = ?", fixture.placeId());

            service.deletePost(fixture.imageId(), fixture.userId());
        });

        assertRemainingPhotoCount(fixture);
    }

    private void assertRemainingPhotoCount(Fixture fixture) {
        assertThat(images.existsById(fixture.imageId())).isFalse();
        assertThat(places.findById(fixture.placeId()).orElseThrow().currentPhotoCount()).isEqualTo(1L);
        assertThat(images.countByMapPlace_IdAndVisibilityStatus(fixture.placeId(), MapImageVisibilityStatus.ACTIVE))
                .isEqualTo(1L);
    }

    private Fixture fixture() {
        return new TransactionTemplate(transactions).execute(status -> {
            User user = users.saveAndFlush(User.builder().username("delete-owner")
                    .email("delete-owner@example.invalid").password("unused").birthYear(1995)
                    .language("ko").country("KR").role(UserRole.USER).build());
            MapPlace place = places.saveAndFlush(MapPlace.builder().name("집계 검증 장소")
                    .address("서울시 중구").latitude(37.5).longitude(127.0).registrant("test")
                    .photoCount(2L).build());
            MapImage image = images.saveAndFlush(image(user, place, "delete-target"));
            images.saveAndFlush(image(user, place, "remaining"));
            PostReport report = reports.saveAndFlush(PostReport.builder().reporterUserId(user.getId())
                    .reporterUsername(user.getUsername()).reportedUserId(user.getId())
                    .reportedImageId(image.getId()).reportedImageUrl(image.getImageUrl())
                    .mapImage(image).reason("test").build());
            return new Fixture(user.getId(), place.getId(), image.getId(), report.getId());
        });
    }

    private MapImage image(User user, MapPlace place, String key) {
        return MapImage.builder().mapPlace(place).userId(user.getId()).username(user.getUsername())
                .title(key).imageUrl("https://example.invalid/" + key).s3Key(key)
                .thumbnailS3Key(key + "-thumbnail").build();
    }

    private record Fixture(Long userId, Long placeId, Long imageId, Long reportId) {
    }
}
