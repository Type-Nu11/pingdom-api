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

    @BeforeEach
    void setUp() {
        reset(objectStorage);
    }

    @Test
    void verificationEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/location-check-ins"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/location-check-ins/{checkInId}/evidence", 1L))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/location-check-ins/{checkInId}/evidence/file", 1L))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/visitor-verification-reports"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/visitor-verification-reports/{reportId}", 1L))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/visitor-verification-reports/{reportId}/corrections", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void visitorVerificationListValidationReturnsStableErrorCode() throws Exception {
        User tourist = userRepository.saveAndFlush(user("reportValidationTourist", UserRole.USER));

        mockMvc.perform(get("/visitor-verification-reports")
                        .param("page", "0")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(tourist)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    void checkInRejectsInvalidCoordinatesWithIdentifiableValidationCause() throws Exception {
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

    @Test
    void checkInRejectsAuthenticatedNonTouristBeforePlaceLookup() throws Exception {
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

    @Test
    void activeTouristCreatesAndListsOnlyOwnCheckIn() throws Exception {
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

    @Test
    void bannedAndWithdrawnTouristsAreRejectedByAuthenticationFilter() throws Exception {
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

    @Test
    void evidenceMetadataIsHiddenFromAnotherAuthenticatedUser() throws Exception {
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

    @Test
    void otherUserCannotUploadOrDownloadEvidenceAndStorageIsNotAccessed() throws Exception {
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

    @Test
    void uploadRejectsSignatureAndDeclaredContentTypeMismatchBeforeStorage() throws Exception {
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

    @Test
    void uploadRejectsOversizedEvidenceBeforeStorage() throws Exception {
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

    @Test
    void ownerUploadsAndReadsEvidenceThroughHttpApi() throws Exception {
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

    @Test
    void storageFailuresReturnServiceUnavailableWithoutPersistingEvidence() throws Exception {
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

    @Test
    void evidenceDownloadPreventsCachingAndContentTypeSniffing() throws Exception {
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

    @Test
    void downloadStorageFailureReturnsServiceUnavailable() throws Exception {
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

    private OwnedCheckIn ownedCheckIn(String username) {
        User owner = userRepository.saveAndFlush(user(username, UserRole.USER));
        MapPlace savedPlace = placeRepository.saveAndFlush(place(owner.getId()));
        LocationCheckIn savedCheckIn = checkInRepository.saveAndFlush(
                checkIn(owner.getId(), savedPlace.getId(), Instant.now()));
        return new OwnedCheckIn(owner, savedCheckIn);
    }

    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name());
    }

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

    private S3StorageException storageUnavailable() {
        return new S3StorageException(S3StorageError.CONNECTION_ERROR, "temporary failure", null);
    }

    private record OwnedCheckIn(User user, LocationCheckIn checkIn) {
    }
}
