package com.typenull.pingdom.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostMedia;
import com.typenull.pingdom.community.domain.CommunityPostMediaStatus;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostMediaRepository;
import com.typenull.pingdom.post.infrastructure.storage.image.ImageUploadProcessor;
import com.typenull.pingdom.post.infrastructure.storage.image.ProcessedImageUpload;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class CommunityPostMediaServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-11T01:00:00Z"), ZoneOffset.UTC);
    private final CommunityPostMediaRepository repository = mock(CommunityPostMediaRepository.class);
    private final ImageUploadProcessor processor = mock(ImageUploadProcessor.class);
    private final S3ObjectStorage storage = mock(S3ObjectStorage.class);
    private final S3ObjectDeleteOutboxPublisher deletion = mock(S3ObjectDeleteOutboxPublisher.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final CommunityPostMediaService service = new CommunityPostMediaService(repository, processor, storage, deletion, clock, transactions);
    private final MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[]{1});

    @Test
    void locksInIdOrderAndConnectsInRequestedOrder() {
        CommunityPostMedia first = media(7L, 0);
        CommunityPostMedia second = media(7L, 0);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(first));
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(second));
        service.connect(7L, mock(CommunityPost.class), List.of(2L, 1L));
        var order = inOrder(repository);
        order.verify(repository).findByIdForUpdate(1L);
        order.verify(repository).findByIdForUpdate(2L);
        assertThat(second.getDisplayOrder()).isZero();
        assertThat(first.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicatesAndMoreThanTenBeforeLocking() {
        assertThatThrownBy(() -> service.connect(7L, mock(CommunityPost.class), List.of(1L, 1L))).isInstanceOf(CommunityException.class);
        assertThatThrownBy(() -> service.connect(7L, mock(CommunityPost.class), java.util.stream.LongStream.rangeClosed(1, 11).boxed().toList()))
                .isInstanceOf(CommunityException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void supportsZeroAndTenImages() {
        service.connect(7L, mock(CommunityPost.class), null);
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 10).boxed().toList();
        for (long id : ids) { when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(media(7L, 0))); }
        service.connect(7L, mock(CommunityPost.class), ids);
        for (long id : ids) { verify(repository).findByIdForUpdate(id); }
    }

    @Test
    void validatesEveryImageBeforeChangingConnectionState() {
        CommunityPostMedia owned = media(7L, 0);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(owned));
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(media(8L, 0)));
        assertThatThrownBy(() -> service.connect(7L, mock(CommunityPost.class), List.of(1L, 2L))).hasMessage("본인이 업로드한 사진만 사용할 수 있습니다.");
        assertThat(owned.getStatus()).isEqualTo(CommunityPostMediaStatus.UPLOADED);
    }

    @Test
    void rejectsExpiredAndConnectedImages() {
        CommunityPostMedia expired = media(7L, -24);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.connect(7L, mock(CommunityPost.class), List.of(1L))).hasMessage("게시글 사진의 임시 보관 기간이 만료되었습니다.");
        CommunityPostMedia connected = media(7L, 0);
        connected.connect(mock(CommunityPost.class), 0, LocalDateTime.now(clock));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(connected));
        assertThatThrownBy(() -> service.cancel(7L, 1L)).hasMessage("이미 게시글에 연결된 사진입니다.");
        verifyNoInteractions(deletion);
    }

    @Test
    void compensatesMetadataCommitFailure() {
        prepareUpload();
        doThrow(new IllegalStateException("commit failed")).when(transactions).commit(any());
        assertThatThrownBy(() -> service.upload(7L, file)).hasMessage("commit failed");
        verify(storage).delete("community-posts/test.png");
    }

    @Test
    void fallsBackToOutboxWhenCompensationFails() {
        prepareUpload();
        when(repository.saveAndFlush(any())).thenThrow(new IllegalStateException("database failed"));
        doThrow(new IllegalStateException("storage failed")).when(storage).delete(anyString());
        assertThatThrownBy(() -> service.upload(7L, file)).hasMessage("database failed");
        verify(deletion).publish("community-posts/test.png", "COMMUNITY_POST_MEDIA", "UNCOMMITTED", "UPLOAD_ROLLED_BACK");
    }

    @Test
    void expiryUsesLockedRowsAndPublishesDeletion() {
        CommunityPostMedia expired = media(7L, -25);
        when(repository.findExpiredForUpdate(any())).thenReturn(List.of(expired), List.of());
        assertThat(service.purgeExpiredUploads()).isEqualTo(1);
        verify(deletion).publish("key", "COMMUNITY_POST_MEDIA", "null", "UPLOAD_EXPIRED");
        verify(repository).deleteAllInBatch(List.of(expired));
    }

    private CommunityPostMedia media(long userId, int offsetHours) {
        return CommunityPostMedia.upload(userId, "key", "https://example.com/image.png", "image/png", 1,
                LocalDateTime.now(clock).plusHours(offsetHours));
    }

    private void prepareUpload() {
        when(processor.process(file)).thenReturn(new ProcessedImageUpload(new byte[]{1}, "test.png", "image/png", new byte[]{1}, "thumb.png", "image/png", 1, 1, 1, 1));
        when(storage.put(any(byte[].class), anyString(), anyString(), anyString())).thenReturn(new S3ObjectStorage.S3PutResult("community-posts/test.png", "https://example.com/image.png"));
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
