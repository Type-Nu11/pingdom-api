package com.typenull.pingdom.identity.application.command;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3ObjectSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProfileImageDeleteHandlerTest {
    private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");
    private static final String KEY = "users/profile-images/tracked/owned.jpg";
    private final ProfileImageUploadService images = mock(ProfileImageUploadService.class);
    private final S3ObjectStorage storage = mock(S3ObjectStorage.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final ProfileImageDeleteHandler handler = new ProfileImageDeleteHandler(images, storage, mapper,
            new ProfileImageStorageProperties(null, null, null), Clock.fixed(NOW, ZoneOffset.UTC), metrics);
    private final ProfileImageUploadService.DeleteRequest request = new ProfileImageUploadService.DeleteRequest("intent", "attempt");

    @AfterEach void closeMetrics() { metrics.close(); }

    @Test
    void currentReferenceOrStaleAttemptDoesNotTouchStorage() throws Exception {
        handler.handle("event", mapper.writeValueAsString(request));
        verifyNoInteractions(storage);
    }

    @Test
    void latePutReceivesFullFourteenDayObjectAge() throws Exception {
        when(images.deletionKey(request)).thenReturn(KEY);
        when(storage.findObjectSnapshot(KEY)).thenReturn(Optional.of(new S3ObjectSnapshot("etag", NOW.minus(Duration.ofDays(1)), "version")));
        handler.handle("event", mapper.writeValueAsString(request));
        verify(images).recheckAfter(request, NOW.plus(Duration.ofDays(13)));
        verify(storage, never()).deleteIfUnchanged(anyString(), any());
    }

    @Test
    void referenceAppearingAfterHeadPreventsDeletion() throws Exception {
        when(images.deletionKey(request)).thenReturn(KEY, null);
        when(storage.findObjectSnapshot(KEY)).thenReturn(Optional.of(new S3ObjectSnapshot("etag", NOW.minus(Duration.ofDays(14)), "version")));
        handler.handle("event", mapper.writeValueAsString(request));
        verify(storage, never()).deleteIfUnchanged(anyString(), any());
    }

    @Test
    void deletesOnlyObservedImmutableVersionAndKeepsRecheck() throws Exception {
        when(images.deletionKey(request)).thenReturn(KEY);
        var snapshot = new S3ObjectSnapshot("etag", NOW.minus(Duration.ofDays(14)), "immutable-version");
        when(storage.findObjectSnapshot(KEY)).thenReturn(Optional.of(snapshot));
        handler.handle("event", mapper.writeValueAsString(request));
        verify(storage).deleteIfUnchanged(KEY, snapshot);
        verify(storage, never()).delete(anyString());
        verify(images).recheckAfter(request, NOW.plus(Duration.ofDays(1)));
    }

    @Test
    void missingVersionIsDeferredAndMeasured() throws Exception {
        when(images.deletionKey(request)).thenReturn(KEY);
        when(storage.findObjectSnapshot(KEY)).thenReturn(Optional.of(new S3ObjectSnapshot("etag", NOW.minus(Duration.ofDays(14)), null)));
        handler.handle("event", mapper.writeValueAsString(request));
        verify(storage, never()).deleteIfUnchanged(anyString(), any());
        verify(images).recheckAfter(request, NOW.plus(Duration.ofDays(1)));
        assertThat(metrics.get("profile.image.cleanup.deferred").tag("reason", "missing_immutable_version").counter().count()).isEqualTo(1);
    }

    @Test
    void configurationFailurePropagatesToOutboxRetry() {
        when(images.deletionKey(request)).thenReturn(KEY);
        when(storage.findObjectSnapshot(KEY)).thenThrow(new S3ObjectStorage.S3StorageException(
                S3ObjectStorage.S3StorageError.NOT_CONFIGURED, "missing", null));
        assertThatThrownBy(() -> handler.handle("event", mapper.writeValueAsString(request)))
                .isInstanceOf(S3ObjectStorage.S3StorageException.class);
        verify(images, never()).recheckAfter(any(), any());
    }
}
