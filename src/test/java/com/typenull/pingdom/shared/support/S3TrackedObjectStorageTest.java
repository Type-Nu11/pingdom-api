package com.typenull.pingdom.shared.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

class S3TrackedObjectStorageTest {
    private final S3Client client = mock(S3Client.class);
    @SuppressWarnings("unchecked") private final ObjectProvider<S3Client> clients = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked") private final ObjectProvider<software.amazon.awssdk.services.s3.presigner.S3Presigner> presigners = mock(ObjectProvider.class);
    private final S3ObjectStorage storage = new S3ObjectStorage(clients, presigners);
    private static final Instant TIME = Instant.parse("2026-11-12T00:00:00Z");

    @BeforeEach
    void setUp() {
        when(clients.getIfAvailable()).thenReturn(client);
        ReflectionTestUtils.setField(storage, "bucket", "test-bucket");
    }

    @Test
    void onlyNotFoundIsAbsence() {
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        assertThat(storage.findObjectSnapshot("key")).isEmpty();
        doThrow(S3Exception.builder().statusCode(403).build()).when(client).headObject(any(HeadObjectRequest.class));
        assertThatThrownBy(() -> storage.findObjectSnapshot("key")).isInstanceOf(S3ObjectStorage.S3StorageException.class);
    }

    @Test
    void conditionalDeleteCarriesExactVersionAndEtag() {
        var snapshot = new S3ObjectStorage.S3ObjectSnapshot("etag", TIME, "v1");
        assertThat(storage.deleteIfUnchanged("key", snapshot)).isTrue();
        var captured = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(captured.capture());
        assertThat(captured.getValue().key()).isEqualTo("key");
        assertThat(captured.getValue().versionId()).isEqualTo("v1");
        assertThat(captured.getValue().ifMatch()).isEqualTo("etag");
    }

    @Test
    void changedObjectIsNotRetriedAsAnUnconditionalDelete() {
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(412).build());
        assertThat(storage.deleteIfUnchanged("key", new S3ObjectStorage.S3ObjectSnapshot("etag", TIME, "v1"))).isFalse();
        verify(client, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void missingOrSuspendedVersionNeverIssuesDelete() {
        for (String version : new String[]{null, "", "null"}) {
            assertThatThrownBy(() -> storage.deleteIfUnchanged("key",
                    new S3ObjectStorage.S3ObjectSnapshot("same-content-etag", TIME, version)))
                    .isInstanceOf(S3ObjectStorage.S3StorageException.class);
        }
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void trackedUploadSetsSdkTotalDeadline() {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("timeout"));
        assertThatThrownBy(() -> storage.putAtKey(new byte[]{1}, "image/jpeg", "key", Duration.ofMinutes(2)))
                .isInstanceOf(S3ObjectStorage.S3StorageException.class);
        var captured = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(captured.capture(), any(RequestBody.class));
        assertThat(captured.getValue().overrideConfiguration().orElseThrow().apiCallTimeout()).contains(Duration.ofMinutes(2));
    }
}
