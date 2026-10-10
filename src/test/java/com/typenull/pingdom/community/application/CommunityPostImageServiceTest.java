package com.typenull.pingdom.community.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.typenull.pingdom.community.domain.CommunityPostImage;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.*;
import com.typenull.pingdom.identity.application.command.ProfileImageFileValidator;
import com.typenull.pingdom.shared.support.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;

class CommunityPostImageServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-11T00:00:00Z"), ZoneOffset.UTC);
    private final CommunityPostImageRepository images = mock(CommunityPostImageRepository.class);
    private final S3ObjectStorage storage = mock(S3ObjectStorage.class);
    private final S3ObjectDeleteOutboxPublisher deletes = mock(S3ObjectDeleteOutboxPublisher.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final CommunityPostImageService service = new CommunityPostImageService(images,
            mock(CommunityPostRepository.class), storage, deletes, new ProfileImageFileValidator(), clock, manager);

    @Test void failedUploadKeepsPreviouslyCommittedKeyForCleanup() {
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(images.saveAndFlush(any())).thenAnswer(call -> {
            CommunityPostImage image=call.getArgument(0); ReflectionTestUtils.setField(image,"id",7L); return image;
        });
        doThrow(new S3ObjectStorage.S3StorageException(S3ObjectStorage.S3StorageError.CONNECTION_ERROR,"test",null))
                .when(storage).putAtKey(any(),anyString(),anyString());
        byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6S0AAAAASUVORK5CYII=");
        assertThatThrownBy(() -> service.upload(1L,new MockMultipartFile("file","photo.png","image/png",png)))
                .isInstanceOf(CommunityException.class);
        var order=inOrder(images,manager,storage);
        order.verify(images).saveAndFlush(any());
        order.verify(manager).commit(any());
        order.verify(storage).putAtKey(any(),anyString(),anyString());
        verify(images,never()).delete(any());
    }

    @Test void expiryPublishesRetryableDeleteBeforeRemovingLedger() {
        CommunityPostImage image=CommunityPostImage.pending(1L,"community/1/expired.png",LocalDateTime.now(clock).minusHours(1));
        ReflectionTestUtils.setField(image,"id",7L);
        when(images.findExpiredForUpdate(any())).thenReturn(List.of(image));
        service.cleanupExpired();
        var order=inOrder(deletes,images);
        order.verify(deletes).publish("community/1/expired.png","COMMUNITY_IMAGE","7","UNUSED_EXPIRED");
        order.verify(images).delete(image);
    }

    @Test void rejectedAttachmentLeavesEveryImageUnattached() {
        CommunityPostImage own=CommunityPostImage.pending(1L,"community/1/own.png",LocalDateTime.now(clock).plusHours(1));
        CommunityPostImage other=CommunityPostImage.pending(2L,"community/2/other.png",LocalDateTime.now(clock).plusHours(1));
        own.uploaded("https://example.invalid/own");other.uploaded("https://example.invalid/other");
        ReflectionTestUtils.setField(own,"id",7L);ReflectionTestUtils.setField(other,"id",8L);
        when(images.findAllByIdForUpdate(any())).thenReturn(List.of(own,other));
        assertThatThrownBy(() -> service.attach(1L,10L,List.of(7L,8L))).isInstanceOf(CommunityException.class);
        assertThat(own.getPostId()).isNull();assertThat(other.getPostId()).isNull();
    }
}
