package com.typenull.pingdom.place.application.service.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.application.service.merchant.MerchantApplicationApprovalService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitation;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitationStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceInvitationRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMemberRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.infrastructure.crypto.MerchantVerificationCipher;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import com.typenull.pingdom.offer.domain.OfferStatus;
import com.typenull.pingdom.offer.domain.TouristOffer;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import com.typenull.pingdom.place.api.dto.registration.MerchantPlaceApplicationReviewRequest;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationReviewHistory;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachmentType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationCategory;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.MerchantPlaceApplicationReviewHistoryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
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
class MerchantPlaceApplicationApprovalPostgreSqlIntegrationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 0, 0);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC);
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    ).withDatabaseName("pingdom").withUsername("pingdom").withPassword("pingdom");

    @Autowired private PlaceRegistrationApplicationRepository applicationRepository;
    @Autowired private MapPlaceRepository placeRepository;
    @Autowired private PlaceRegistrationService placeRegistrationService;
    @Autowired private MerchantOwnerPlaceRepository ownerPlaceRepository;
    @Autowired private MerchantOwnerProfileRepository profileRepository;
    @Autowired private MerchantVerificationRepository verificationRepository;
    @Autowired private MerchantPlaceMemberRepository memberRepository;
    @Autowired private MerchantPlaceInvitationRepository invitationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TouristOfferRepository offerRepository;
    @Autowired private AdminAuditLogRepository auditRepository;
    @Autowired private AdminAuditLogService auditService;
    @Autowired private MerchantPlaceApplicationReviewHistoryRepository historyRepository;
    @Autowired private MerchantApplicationApprovalService identityApproval;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private S3ObjectStorage storage;
    private TransactionTemplate transaction;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE admin_audit_log, merchant_place_application_review_history, users, map_place RESTART IDENTITY CASCADE");
        transaction = new TransactionTemplate(transactionManager);
        when(storage.publicUrl(anyString())).thenAnswer(invocation -> "https://example.test/" + invocation.getArgument(0));
    }

    static Stream<Arguments> approvalFailures() {
        return Stream.of(MerchantPlaceApplicationType.NEW_PLACE, MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM)
                .flatMap(type -> Stream.of("identity", "audit", "history").map(stage -> Arguments.of(type, stage)));
    }

    // 실제 커밋된 fixture를 사용하고 실패 트랜잭션 종료 뒤 새 트랜잭션에서 원자성을 확인합니다.
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("approvalFailures")
    void rollsBackApprovalAndAllParticipatingState(MerchantPlaceApplicationType type, String stage) {
        Fixture fixture = fixture(type);
        MerchantApplicationApprovalService writer = mock(MerchantApplicationApprovalService.class, delegatesTo(identityApproval));
        AdminAuditLogService audit = mock(AdminAuditLogService.class, delegatesTo(auditService));
        MerchantPlaceApplicationReviewHistoryRepository history = mock(
                MerchantPlaceApplicationReviewHistoryRepository.class, delegatesTo(historyRepository));
        RuntimeException failure = new RuntimeException("approval " + stage + " failure");
        if (stage.equals("identity")) {
            doAnswer(invocation -> {
                identityApproval.approveVerification(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2), invocation.getArgument(3));
                throw failure;
            }).when(writer).approveVerification(any(), any(), any(), any());
        } else if (stage.equals("audit")) {
            doAnswer(invocation -> {
                auditService.record(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                        invocation.getArgument(3), invocation.getArgument(4), invocation.getArgument(5), invocation.getArgument(6));
                throw failure;
            }).when(audit).record(any(), any(), any(), any(), any(), any(), any());
        } else {
            doAnswer(invocation -> {
                historyRepository.saveAndFlush(invocation.getArgument(0));
                throw failure;
            }).when(history).save(any(MerchantPlaceApplicationReviewHistory.class));
        }
        MerchantPlaceApplicationService service = service(writer, audit, history);

        assertThatThrownBy(() -> transaction.execute(status -> service.approve(fixture.adminId(), fixture.applicationId(),
                new MerchantPlaceApplicationReviewRequest(fixture.version(), "승인")))).isSameAs(failure);

        transaction.executeWithoutResult(status -> {
            PlaceRegistrationApplication application = applicationRepository.findById(fixture.applicationId()).orElseThrow();
            assertThat(application.getStatus()).isEqualTo(PlaceRegistrationStatus.PENDING);
            assertThat(application.getCompletedPlaceId()).isNull();
            assertThat(userRepository.findById(fixture.applicantId()).orElseThrow().getRole()).isEqualTo(UserRole.USER);
            MerchantOwnerProfile profile = profileRepository.findById(fixture.applicantId()).orElseThrow();
            assertThat(profile.getStatus()).isEqualTo(MerchantOwnerStatus.PENDING);
            assertThat(profile.getDisplayName()).isEqualTo("기존 표시");
            MerchantVerification verification = verificationRepository.findById(fixture.applicantId()).orElseThrow();
            assertThat(verification.getLegalName()).isEqualTo("기존 이름");
            assertThat(verification.getIdentityStatus()).isEqualTo(MerchantVerificationStatus.PENDING);
            assertThat(verification.getBusinessStatus()).isEqualTo(MerchantVerificationStatus.PENDING);
            assertThat(placeRepository.count()).isEqualTo(1);
            assertThat(ownerPlaceRepository.count()).isEqualTo(1);
            assertThat(ownerPlaceRepository.findById(fixture.placeId()).orElseThrow().getMerchantOwnerUserId())
                    .isEqualTo(fixture.previousOwnerId());
            assertThat(memberRepository.findByPlaceIdAndUserIdForUpdate(fixture.placeId(), fixture.previousOwnerId())
                    .orElseThrow().getStatus()).isEqualTo(MerchantPlaceMemberStatus.ACTIVE);
            assertThat(memberRepository.findByPlaceIdAndUserIdForUpdate(fixture.placeId(), fixture.applicantId())
                    .orElseThrow().getStatus()).isEqualTo(MerchantPlaceMemberStatus.REVOKED);
            assertThat(invitationRepository.findById(fixture.invitationId()).orElseThrow().getStatus())
                    .isEqualTo(MerchantPlaceInvitationStatus.PENDING);
            assertThat(offerRepository.findById(fixture.offerId()).orElseThrow().getStatus()).isEqualTo(OfferStatus.PUBLISHED);
            assertThat(auditRepository.count()).isZero();
            assertThat(historyRepository.count()).isZero();
        });
    }

    @Test
    void commitsClaimCompletionAndSnapshotsAcrossBulkClear() {
        Fixture fixture = fixture(MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM);
        transaction.execute(status -> service(identityApproval, auditService, historyRepository).approve(
                fixture.adminId(), fixture.applicationId(), new MerchantPlaceApplicationReviewRequest(fixture.version(), "승인")));

        transaction.executeWithoutResult(status -> {
            assertThat(applicationRepository.findById(fixture.applicationId()).orElseThrow().getStatus())
                    .isEqualTo(PlaceRegistrationStatus.COMPLETED);
            assertThat(ownerPlaceRepository.findById(fixture.placeId()).orElseThrow().getMerchantOwnerUserId())
                    .isEqualTo(fixture.applicantId());
            assertThat(memberRepository.findByPlaceIdAndUserIdForUpdate(fixture.placeId(), fixture.applicantId())
                    .orElseThrow().getRole()).isEqualTo(MerchantPlaceMemberRole.OWNER);
            assertThat(offerRepository.findById(fixture.offerId()).orElseThrow().getStatus()).isEqualTo(OfferStatus.CLOSED);
            var history = historyRepository.findAllByApplicationIdOrderByCreatedAtDescIdDesc(fixture.applicationId());
            assertThat(history).hasSize(1);
            assertThat(history.get(0).getOfferSnapshot()).contains("PUBLISHED");
            assertThat(history.get(0).getTeamSnapshot()).contains("REVOKED", "OWNER", "ACTIVE");
            assertThat(auditRepository.count()).isEqualTo(1);
        });
    }

    @Test
    void refusesIdentityParticipationWithoutCallingTransaction() {
        assertThatThrownBy(() -> identityApproval.approveVerification(1L, 2L, "승인", NOW))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private Fixture fixture(MerchantPlaceApplicationType type) {
        return transaction.execute(status -> {
            User applicant = user("applicant", UserRole.USER);
            User previousOwner = user("previous", UserRole.MERCHANT_OWNER);
            User admin = user("admin", UserRole.ADMIN);
            MapPlace place = placeRepository.saveAndFlush(MapPlace.builder().name("기존 장소").address("기존 주소")
                    .latitude(35.1).longitude(128.1).userId(previousOwner.getId()).registrant("previous").build());
            ownerPlaceRepository.saveAndFlush(MerchantOwnerPlace.builder().placeId(place.getId())
                    .merchantOwnerUserId(previousOwner.getId()).createdAt(NOW.minusDays(1)).build());
            memberRepository.saveAndFlush(MerchantPlaceMember.owner(place.getId(), previousOwner.getId(), NOW.minusDays(1)));
            MerchantPlaceMember revoked = MerchantPlaceMember.create(
                    place.getId(), applicant.getId(), MerchantPlaceMemberRole.STAFF, previousOwner.getId(), NOW.minusDays(1));
            revoked.revoke(NOW.minusHours(1));
            memberRepository.saveAndFlush(revoked);
            MerchantPlaceInvitation invitation = invitationRepository.saveAndFlush(MerchantPlaceInvitation.pending(
                    place.getId(), admin.getId(), previousOwner.getId(), MerchantPlaceMemberRole.STAFF, NOW.plusDays(1), NOW));
            TouristOffer offer = TouristOffer.draft(previousOwner.getId(), place.getId(), "혜택", "소개", "할인",
                    NOW.minusDays(1), NOW.plusDays(1), 10, 1, NOW.minusDays(1));
            offer.publish(NOW);
            offerRepository.saveAndFlush(offer);
            profileRepository.saveAndFlush(MerchantOwnerProfile.pending(
                    applicant.getId(), "핑덤", "기존 표시", "기존 소개", "old@example.com", "+821012345678", NOW.minusDays(1)));
            verificationRepository.saveAndFlush(MerchantVerification.pending(
                    applicant.getId(), "기존 이름", "핑덤", "old-encrypted", NOW.minusDays(1)));
            PlaceRegistrationApplication application = PlaceRegistrationApplication.merchantPlaceDraft(applicant.getId(),
                    "신규 장소", PlaceRegistrationCategory.CAFE, 35.2, 128.2, "신규 주소", null, null, "장소 소개", Set.of(), NOW);
            application.configureMerchantSubmission(type, "홍길동", "핑덤", "encrypted", "새 표시", "new@example.com", "소개",
                    "+821012345678", place.getId(), previousOwner.getId(), "소유권 신청", NOW);
            application.updateOperatingSchedule("Asia/Seoul", "[]", NOW);
            application.replaceAttachments(List.of(
                    attachment(application, PlaceRegistrationAttachmentType.BUSINESS_REGISTRATION),
                    attachment(application, PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT),
                    attachment(application, PlaceRegistrationAttachmentType.REPRESENTATIVE_IMAGE)), NOW);
            application.submit(NOW);
            applicationRepository.saveAndFlush(application);
            return new Fixture(application.getId(), application.getVersion(), applicant.getId(), admin.getId(),
                    place.getId(), previousOwner.getId(), invitation.getId(), offer.getId());
        });
    }

    private MerchantPlaceApplicationService service(MerchantApplicationApprovalService writer, AdminAuditLogService audit,
            MerchantPlaceApplicationReviewHistoryRepository history) {
        return new MerchantPlaceApplicationService(applicationRepository, placeRepository, placeRegistrationService,
                ownerPlaceRepository, memberRepository, invitationRepository, userRepository, mock(UserAccessStatusService.class),
                mock(MerchantVerificationCipher.class), mock(AdminRoleAuthorizationService.class), audit, offerRepository,
                history, objectMapper, CLOCK, mock(MerchantPlaceApplicationAdminQueryService.class),
                mock(MerchantPlaceApplicationAdminAttachmentService.class),
                new MerchantPlaceApplicationReviewSnapshotSerializer(objectMapper), writer);
    }

    private PlaceRegistrationAttachment attachment(PlaceRegistrationApplication application, PlaceRegistrationAttachmentType type) {
        return PlaceRegistrationAttachment.create(application, null, type, "private/applications/test/" + type + ".png",
                type + ".png", "image/png", 10L, "a".repeat(64), application.getApplicantUserId(), NOW, null, 0);
    }

    private User user(String name, UserRole role) {
        return userRepository.saveAndFlush(User.builder().username(name).email(name + "@example.com").password("password")
                .birthYear(1990).language("ko").country("KR").role(role).build());
    }

    private record Fixture(Long applicationId, long version, Long applicantId, Long adminId, Long placeId,
                           Long previousOwnerId, Long invitationId, Long offerId) { }
}
