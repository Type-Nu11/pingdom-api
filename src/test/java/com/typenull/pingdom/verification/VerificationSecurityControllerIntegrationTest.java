package com.typenull.pingdom.verification;

import static com.typenull.pingdom.verification.VerificationSecurityFixture.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3PutResult;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageError;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageException;
import com.typenull.pingdom.verification.domain.LocationCheckIn;
import com.typenull.pingdom.verification.domain.VisitEvidence;
import com.typenull.pingdom.verification.infrastructure.LocationCheckInRepository;
import com.typenull.pingdom.verification.infrastructure.VisitEvidenceRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Tag("integration")
@SpringBootTest(properties = "verification.visit-evidence.max-file-size-bytes=1024")
@AutoConfigureMockMvc
@Transactional
class VerificationSecurityControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private MapPlaceRepository placeRepository;
    @Autowired private LocationCheckInRepository checkInRepository;
    @Autowired private VisitEvidenceRepository evidenceRepository;

    @MockBean
    private S3ObjectStorage objectStorage;

    /** 각 요청 시나리오가 이전 S3 stubbing과 호출 기록에 영향을 받지 않도록 mock을 초기화한다. */
    @BeforeEach
    void setUp() {
        reset(objectStorage);
    }

    /** 토큰 없이 체크인·증빙 메타데이터/파일·세션 조회/관측·제보/정정 조회에 접근하면 모두 401이어야 한다. */
    @Test
    void requireEndpointAuthentication() throws Exception {
        mockMvc.perform(get("/location-check-ins"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/location-check-ins/{checkInId}/evidence", 1L))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/location-check-ins/{checkInId}/evidence/file", 1L))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/visit-verification-sessions/{sessionId}", 1L))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/visit-verification-sessions/{sessionId}/observations", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/visitor-verification-reports"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/visitor-verification-reports/{reportId}", 1L))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/visitor-verification-reports/{reportId}/corrections", 1L))
                .andExpect(status().isUnauthorized());
    }

    /** 인증 관광객이 page=0으로 제보 목록을 요청하면 400과 VALIDATION_FAILED, 비어 있지 않은 오류 목록을 반환한다. */
    @Test
    void rejectInvalidListPage() throws Exception {
        User tourist = userRepository.saveAndFlush(user("reportValidationTourist", UserRole.USER));

        mockMvc.perform(get("/visitor-verification-reports")
                        .param("page", "0")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    /** 체크인 위도 91은 400이며 공통 입력 오류 메시지와 latitude 필드 오류가 있어야 한다. */
    @Test
    void rejectInvalidLatitude() throws Exception {
        User tourist = userRepository.saveAndFlush(user("coordinateTourist", UserRole.USER));

        mockMvc.perform(post("/location-check-ins")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "placeId", 1,
                                "latitude", 91.0,
                                "longitude", PLACE_LONGITUDE,
                                "accuracyMeters", 10.0,
                                "observedAt", Instant.now().toString()
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값을 확인해주세요."))
                .andExpect(jsonPath("$.errors.latitude").isNotEmpty());
    }

    /** 유효한 관리자 토큰으로 존재하지 않는 장소에 체크인해도 장소 부재 대신 관광객 계정 필요 403을 반환한다. */
    @Test
    void rejectAdminCheckIn() throws Exception {
        User admin = userRepository.saveAndFlush(user("checkInAdmin", UserRole.ADMIN));

        mockMvc.perform(post("/location-check-ins")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "placeId", 999999,
                                "latitude", PLACE_LATITUDE,
                                "longitude", PLACE_LONGITUDE,
                                "accuracyMeters", 10.0,
                                "observedAt", Instant.now().toString()
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TOURIST_ACCOUNT_REQUIRED"));
    }

    /**
     * 관광객은 장소에 근접 체크인을 201로 생성할 수 있다.
     * 본인 목록은 1건, 다른 관광객 목록은 0건으로 소유자별 조회를 구분한다.
     */
    @Test
    void listOnlyOwnedCheckIns() throws Exception {
        User owner = userRepository.saveAndFlush(user("successfulCheckInOwner", UserRole.USER));
        User other = userRepository.saveAndFlush(user("successfulCheckInOther", UserRole.USER));
        MapPlace savedPlace = placeRepository.saveAndFlush(place(owner.getId()));

        mockMvc.perform(post("/location-check-ins")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "placeId", savedPlace.getId(),
                                "latitude", PLACE_LATITUDE,
                                "longitude", PLACE_LONGITUDE,
                                "accuracyMeters", 10.0,
                                "observedAt", Instant.now().toString()
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placeId").value(savedPlace.getId()))
                .andExpect(jsonPath("$.status").value("PROXIMITY_MATCHED"));

        mockMvc.perform(get("/location-check-ins")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/location-check-ins")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /** 토큰 발급 후 계정을 정지·탈퇴시키면 기존 토큰 요청이 각각 INVALID_TOKEN 401로 거부되어야 한다. */
    @Test
    void rejectDisabledAccountTokens() throws Exception {
        User banned = userRepository.saveAndFlush(user("bannedCheckInTourist", UserRole.USER));
        String bannedToken = bearerToken(banned);
        banned.ban("security fixture", LocalDateTime.now());
        userRepository.saveAndFlush(banned);

        User withdrawn = userRepository.saveAndFlush(user("withdrawnCheckInTourist", UserRole.USER));
        String withdrawnToken = bearerToken(withdrawn);
        withdrawn.withdraw("withdrawn-user", "withdrawn@example.com", "disabled", LocalDateTime.now());
        userRepository.saveAndFlush(withdrawn);

        assertInvalidToken(bannedToken);
        assertInvalidToken(withdrawnToken);
    }

    /** 타인의 증빙이 DB에 있어도 메타데이터 요청은 CHECK_IN_NOT_FOUND 404로 반환한다. */
    @Test
    void hideForeignEvidenceMetadata() throws Exception {
        OwnedCheckIn owned = ownedCheckIn("evidenceOwner");
        User other = userRepository.saveAndFlush(user("evidenceOther", UserRole.USER));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        evidenceRepository.saveAndFlush(VisitEvidence.create(
                owned.checkIn().getId(), owned.user().getId(), "visit-evidence/private-key", "visit.jpg",
                "image/jpeg", 100, now, now.plus(30, ChronoUnit.DAYS)));

        mockMvc.perform(get("/location-check-ins/{checkInId}/evidence", owned.checkIn().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHECK_IN_NOT_FOUND"));
    }

    /** 타인 체크인에 업로드·다운로드 요청을 하면 모두 CHECK_IN_NOT_FOUND 404이며 S3 접근이 없어야 한다. */
    @Test
    void rejectForeignEvidenceAccess() throws Exception {
        OwnedCheckIn owned = ownedCheckIn("privateEvidenceOwner");
        User other = userRepository.saveAndFlush(user("privateEvidenceOther", UserRole.USER));
        MockMultipartFile file = new MockMultipartFile("file", "visit.jpg", "image/jpeg", jpegBytes());

        mockMvc.perform(multipart("/location-check-ins/{checkInId}/evidence", owned.checkIn().getId())
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHECK_IN_NOT_FOUND"));
        mockMvc.perform(get("/location-check-ins/{checkInId}/evidence/file", owned.checkIn().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHECK_IN_NOT_FOUND"));

        verifyNoInteractions(objectStorage);
    }

    /** JPEG를 PNG라고 선언한 업로드는 파일 형식 오류 400으로 거부하며 S3를 호출하지 않는다. */
    @Test
    void rejectMismatchedEvidenceType() throws Exception {
        OwnedCheckIn owned = ownedCheckIn("invalidFileOwner");
        MockMultipartFile invalid = new MockMultipartFile(
                "file", "fake.png", "image/png", jpegBytes());

        mockMvc.perform(multipart("/location-check-ins/{checkInId}/evidence", owned.checkIn().getId())
                        .file(invalid)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owned.user())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VISIT_EVIDENCE_FILE_INVALID"));

        verifyNoInteractions(objectStorage);
    }

    /** 1KB 제한에 1,025바이트를 업로드하면 크기 초과 413과 전용 오류 코드를 반환하고 S3를 호출하지 않는다. */
    @Test
    void rejectOversizedEvidence() throws Exception {
        OwnedCheckIn owned = ownedCheckIn("oversizedFileOwner");
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "oversized.jpg", "image/jpeg", new byte[1025]);

        mockMvc.perform(multipart("/location-check-ins/{checkInId}/evidence", owned.checkIn().getId())
                        .file(oversized)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owned.user())))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("VISIT_EVIDENCE_FILE_TOO_LARGE"));

        verifyNoInteractions(objectStorage);
    }

    /**
     * 본인 체크인에 유효 JPEG를 업로드하면 201과 체크인 ID·MIME 타입을 반환한다.
     * 이어 메타데이터를 조회하면 저장한 원본 파일명이 반환되어야 한다.
     */
    @Test
    void uploadAndReadOwnedEvidence() throws Exception {
        OwnedCheckIn owned = ownedCheckIn("successfulEvidenceOwner");
        MockMultipartFile file = new MockMultipartFile("file", "visit.jpg", "image/jpeg", jpegBytes());
        when(objectStorage.put(any(byte[].class), anyString(), eq("image/jpeg"), eq("visit-evidence")))
                .thenReturn(new S3PutResult("visit-evidence/success-key", "unused"));

        mockMvc.perform(multipart("/location-check-ins/{checkInId}/evidence", owned.checkIn().getId())
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owned.user())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationCheckInId").value(owned.checkIn().getId()))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"));

        mockMvc.perform(get("/location-check-ins/{checkInId}/evidence", owned.checkIn().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owned.user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("visit.jpg"));
    }

    /** S3 업로드 연결 실패는 저장소 사용 불가 503으로 변환되고 DB 증빙 행이 남지 않아야 한다. */
    @Test
    void rejectFailedEvidenceUpload() throws Exception {
        OwnedCheckIn owned = ownedCheckIn("storageFailureOwner");
        MockMultipartFile file = new MockMultipartFile("file", "visit.jpg", "image/jpeg", jpegBytes());
        when(objectStorage.put(any(byte[].class), anyString(), eq("image/jpeg"), eq("visit-evidence")))
                .thenThrow(storageUnavailable());

        mockMvc.perform(multipart("/location-check-ins/{checkInId}/evidence", owned.checkIn().getId())
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owned.user())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("VISIT_EVIDENCE_STORAGE_UNAVAILABLE"));

        org.assertj.core.api.Assertions.assertThat(evidenceRepository.count()).isZero();
    }

    /** 다운로드는 JPEG 콘텐츠 타입과 mock 바이트를 반환하고 no-store·nosniff 헤더를 포함해야 한다. */
    @Test
    void returnProtectedImageResponse() throws Exception {
        OwnedCheckIn owned = ownedCheckIn("downloadOwner");
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        evidenceRepository.saveAndFlush(VisitEvidence.create(
                owned.checkIn().getId(), owned.user().getId(), "visit-evidence/download-key", "visit.jpg",
                "image/jpeg", 4, now, now.plus(30, ChronoUnit.DAYS)));
        when(objectStorage.getBytes("visit-evidence/download-key")).thenReturn(new byte[]{1, 2, 3, 4});

        mockMvc.perform(get("/location-check-ins/{checkInId}/evidence/file", owned.checkIn().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owned.user())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(new byte[]{1, 2, 3, 4}));
    }

    /** 소유권을 통과한 다운로드라도 S3 연결이 실패하면 저장소 사용 불가 503과 전용 코드를 반환한다. */
    @Test
    void reportDownloadStorageFailure() throws Exception {
        OwnedCheckIn owned = ownedCheckIn("downloadFailureOwner");
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        evidenceRepository.saveAndFlush(VisitEvidence.create(
                owned.checkIn().getId(), owned.user().getId(), "visit-evidence/failure-key", "visit.jpg",
                "image/jpeg", 4, now, now.plus(30, ChronoUnit.DAYS)));
        when(objectStorage.getBytes("visit-evidence/failure-key")).thenThrow(storageUnavailable());

        mockMvc.perform(get("/location-check-ins/{checkInId}/evidence/file", owned.checkIn().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(owned.user())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("VISIT_EVIDENCE_STORAGE_UNAVAILABLE"));
    }

    /** 사용자·장소·근접 체크인을 DB에 flush하고 소유자와 체크인 식별자를 후속 요청에 제공한다. */
    private OwnedCheckIn ownedCheckIn(String username) {
        User owner = userRepository.saveAndFlush(user(username, UserRole.USER));
        MapPlace savedPlace = placeRepository.saveAndFlush(place(owner.getId()));
        LocationCheckIn savedCheckIn = checkInRepository.saveAndFlush(
                checkIn(owner.getId(), savedPlace.getId(), Instant.now()));
        return new OwnedCheckIn(owner, savedCheckIn);
    }

    /** 저장한 사용자 ID·이름·역할로 액세스 토큰을 발급해 Authorization 헤더 형식으로 반환한다. */
    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name());
    }

    /** 주어진 토큰으로 체크인 요청을 보내 INVALID_TOKEN 401을 확인한다. */
    private void assertInvalidToken(String token) throws Exception {
        mockMvc.perform(post("/location-check-ins")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "placeId", 999999,
                                "latitude", PLACE_LATITUDE,
                                "longitude", PLACE_LONGITUDE,
                                "accuracyMeters", 10.0,
                                "observedAt", Instant.now().toString()
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /** S3 연결 장애를 재현할 저장소 예외를 생성한다. */
    private S3StorageException storageUnavailable() {
        return new S3StorageException(S3StorageError.CONNECTION_ERROR, "temporary failure", null);
    }

    private record OwnedCheckIn(User user, LocationCheckIn checkIn) {
    }
}
