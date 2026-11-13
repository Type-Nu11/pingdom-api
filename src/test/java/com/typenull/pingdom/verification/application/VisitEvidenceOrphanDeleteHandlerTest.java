package com.typenull.pingdom.verification.application;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3ObjectSnapshot;
import com.typenull.pingdom.verification.application.VisitEvidenceUploadIntentService.DeleteRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VisitEvidenceOrphanDeleteHandlerTest {
    private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");
    private static final String KEY = "visit-evidence/tracked/8a7e3f69-b920-4aad-8eb8-8862dfd6e152.jpg";
    private final VisitEvidenceUploadIntentService intents = mock(VisitEvidenceUploadIntentService.class);
    private final S3ObjectStorage storage = mock(S3ObjectStorage.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final VisitEvidenceOrphanProperties properties = new VisitEvidenceOrphanProperties(null, null, null);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final VisitEvidenceOrphanDeleteHandler handler = new VisitEvidenceOrphanDeleteHandler(
            intents, storage, mapper, properties, Clock.fixed(NOW, ZoneOffset.UTC), metrics);
    private final DeleteRequest request = new DeleteRequest("intent", "attempt");

    @Test
    void activeReferenceOrStaleAttemptNeverTouchesStorage() throws Exception {
        handler.handle("event", mapper.writeValueAsString(request));
        verifyNoInteractions(storage);
    }

    @Test
    void latePutRestartsObjectAgeGrace() throws Exception {
        when(intents.deletionKey(request)).thenReturn(KEY);
        var object = new S3ObjectSnapshot("etag", NOW.minus(Duration.ofDays(1)), null);
        when(storage.findObjectSnapshot(KEY)).thenReturn(Optional.of(object));
        handler.handle("event", mapper.writeValueAsString(request));
        verify(intents).recheckAfter(request, NOW.plus(Duration.ofDays(13)));
        verify(storage, never()).deleteIfUnchanged(anyString(), any());
    }

    @Test
    void revalidateReferenceAfterHeadBeforeDelete() throws Exception {
        when(intents.deletionKey(request)).thenReturn(KEY, null);
        var object = new S3ObjectSnapshot("etag", NOW.minus(Duration.ofDays(14)), "version");
        when(storage.findObjectSnapshot(KEY)).thenReturn(Optional.of(object));
        handler.handle("event", mapper.writeValueAsString(request));
        verify(storage, never()).deleteIfUnchanged(anyString(), any());
    }

    @Test
    void deleteKnownVersionAndKeepTombstoneForReappearance() throws Exception {
        when(intents.deletionKey(request)).thenReturn(KEY);
        var object = new S3ObjectSnapshot("etag", NOW.minus(Duration.ofDays(14)), "version");
        when(storage.findObjectSnapshot(KEY)).thenReturn(Optional.of(object));
        handler.handle("event", mapper.writeValueAsString(request));
        verify(storage).deleteIfUnchanged(KEY, object);
        verify(intents).recheckAfter(request, NOW.plus(Duration.ofDays(1)));
    }

    @Test
    void versionlessObjectIsDeferredAndMeasuredWithoutDeletion() throws Exception {
        when(intents.deletionKey(request)).thenReturn(KEY);
        var object = new S3ObjectSnapshot("same-content-etag", NOW.minus(Duration.ofDays(14)), null);
        when(storage.findObjectSnapshot(KEY)).thenReturn(Optional.of(object));
        handler.handle("event", mapper.writeValueAsString(request));
        verify(storage, never()).deleteIfUnchanged(anyString(), any());
        verify(intents).recheckAfter(request, NOW.plus(Duration.ofDays(1)));
        org.assertj.core.api.Assertions.assertThat(metrics.get("visit.evidence.orphan.deferred")
                .tag("reason", "missing_immutable_version").counter().count()).isEqualTo(1.0);
    }

    @Test
    void absentObjectStillSchedulesRecheck() throws Exception {
        when(intents.deletionKey(request)).thenReturn(KEY);
        when(storage.findObjectSnapshot(KEY)).thenReturn(Optional.empty());
        handler.handle("event", mapper.writeValueAsString(request));
        verify(intents).recheckAfter(request, NOW.plus(Duration.ofDays(1)));
        verify(storage, never()).deleteIfUnchanged(anyString(), any());
    }

    @Test
    void storageFailurePropagatesForOutboxRetry() throws Exception {
        when(intents.deletionKey(request)).thenReturn(KEY);
        when(storage.findObjectSnapshot(KEY)).thenThrow(new S3ObjectStorage.S3StorageException(
                S3ObjectStorage.S3StorageError.NOT_CONFIGURED, "missing", null));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.handle("event", mapper.writeValueAsString(request)))
                .isInstanceOf(S3ObjectStorage.S3StorageException.class);
        verify(intents, never()).recheckAfter(any(), any());
    }
}
