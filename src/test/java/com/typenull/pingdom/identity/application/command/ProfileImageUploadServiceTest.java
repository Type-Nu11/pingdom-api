package com.typenull.pingdom.identity.application.command;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.domain.*;
import com.typenull.pingdom.identity.domain.repository.*;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class ProfileImageUploadServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");
    private final ProfileImageUploadIntentRepository intents = mock(ProfileImageUploadIntentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final S3ObjectStorage storage = mock(S3ObjectStorage.class);
    private final OutboxEventPublisher publisher = mock(OutboxEventPublisher.class);
    private final ProfileImageStorageProperties properties = new ProfileImageStorageProperties(null, null, null);
    private final ProfileImageUploadService service = new ProfileImageUploadService(
            intents, users, storage, publisher, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void persistsOwnedKeyAndUrlBeforeExternalPut() {
        when(storage.publicUrl(anyString())).thenAnswer(call -> "https://bucket.example/" + call.getArgument(0));
        when(intents.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var prepared = service.prepare(1L, "jpg");
        assertThat(prepared.key()).contains(prepared.intentId());
        verify(storage, never()).putAtKey(any(), anyString(), anyString(), any());
    }

    @Test
    void successfulReplacementMarksOnlyOwnedPreviousImageOrphan() {
        var current = image(NOW);
        var previous = image(NOW.minusSeconds(60));
        previous.attached(NOW.minusSeconds(60), Duration.ofDays(1));
        User user = User.builder().id(1L).profileImageUrl(previous.getImageUrl()).build();
        when(intents.findForUpdate(current.getId())).thenReturn(Optional.of(current));
        when(intents.findByUserIdAndImageUrlForUpdate(1L, previous.getImageUrl())).thenReturn(Optional.of(previous));
        assertThat(service.complete(current.getId(), user)).isEqualTo(current.getImageUrl());
        assertThat(current.getState()).isEqualTo(ProfileImageUploadIntent.State.ATTACHED);
        assertThat(previous.getState()).isEqualTo(ProfileImageUploadIntent.State.ORPHAN);
        assertThat(previous.getOrphanedAt()).isEqualTo(NOW);
        verify(users).saveAndFlush(user);
    }

    @Test
    void failedUserVersionLeavesIntentUnattachedForRecovery() {
        var current = image(NOW);
        User user = User.builder().id(1L).build();
        when(intents.findForUpdate(current.getId())).thenReturn(Optional.of(current));
        when(users.saveAndFlush(user)).thenThrow(new ObjectOptimisticLockingFailureException(User.class, 1L));
        assertThatThrownBy(() -> service.complete(current.getId(), user)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(current.getState()).isEqualTo(ProfileImageUploadIntent.State.UPLOADING);
        verify(intents, never()).findByUserIdAndImageUrlForUpdate(anyLong(), anyString());
    }

    @Test
    void deadlineExceededDuringUserFlushRejectsWholeTransaction() {
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, NOW.plus(Duration.ofMinutes(3)));
        var timed = new ProfileImageUploadService(intents, users, storage, publisher, properties, clock);
        var current = image(NOW);
        User user = User.builder().id(1L).build();
        when(intents.findForUpdate(current.getId())).thenReturn(Optional.of(current));
        assertThatThrownBy(() -> timed.complete(current.getId(), user))
                .isInstanceOf(com.typenull.pingdom.identity.domain.exception.UsersException.class);
        verify(users).saveAndFlush(user);
        assertThat(current.getState()).isEqualTo(ProfileImageUploadIntent.State.UPLOADING);
    }

    @Test
    void expiredUploadStartsGraceAndYoungOrphanDoesNotQueue() {
        var expired = image(NOW.minus(Duration.ofDays(20)));
        var young = image(NOW.minus(Duration.ofDays(14)));
        young.orphaned(NOW.minus(Duration.ofDays(14)).plusSeconds(1));
        when(intents.findDueForUpdate(NOW, 50)).thenReturn(List.of(expired, young));
        assertThat(service.queueDueCleanup()).isZero();
        assertThat(expired.getOrphanedAt()).isEqualTo(NOW);
        verifyNoInteractions(publisher);
    }

    @Test
    void sharedCurrentUrlAndLiveUploadArePreserved() {
        var shared = orphan();
        var uploading = image(NOW);
        when(intents.findDueForUpdate(NOW, 50)).thenReturn(List.of(shared, uploading));
        when(users.existsByProfileImageUrl(shared.getImageUrl())).thenReturn(true);
        assertThat(service.queueDueCleanup()).isZero();
        assertThat(shared.getState()).isEqualTo(ProfileImageUploadIntent.State.ATTACHED);
        assertThat(uploading.getState()).isEqualTo(ProfileImageUploadIntent.State.UPLOADING);
        verifyNoInteractions(publisher);
    }

    @Test
    void dueOrphanUsesAttemptScopedOutboxAndRevalidatesCurrentReference() {
        var old = orphan();
        when(intents.findDueForUpdate(NOW, 50)).thenReturn(List.of(old));
        assertThat(service.queueDueCleanup()).isEqualTo(1);
        var request = new ProfileImageUploadService.DeleteRequest(old.getId(), old.getDeleteAttempt());
        verify(publisher).publish(eq("PROFILE_IMAGE_DELETE:" + old.getId() + ":" + request.attempt()),
                eq(OutboxEventType.PROFILE_IMAGE_DELETE_REQUESTED), eq(request), eq("PROFILE_IMAGE_UPLOAD"), eq(old.getId()));
        when(intents.findForUpdate(old.getId())).thenReturn(Optional.of(old));
        when(users.existsByProfileImageUrl(old.getImageUrl())).thenReturn(true);
        assertThat(service.deletionKey(request)).isNull();
        assertThat(old.getDeleteAttempt()).isNull();
    }

    @Test
    void staleOutboxCannotDeleteOrRescheduleNewAttempt() {
        var old = orphan();
        String before = old.queueDeletion(NOW, Duration.ofDays(1));
        String current = old.queueDeletion(NOW, Duration.ofDays(1));
        when(intents.findForUpdate(old.getId())).thenReturn(Optional.of(old));
        var stale = new ProfileImageUploadService.DeleteRequest(old.getId(), before);
        assertThat(service.deletionKey(stale)).isNull();
        service.recheckAfter(stale, NOW);
        assertThat(old.getDeleteAttempt()).isEqualTo(current);
        assertThat(old.getNextCheckAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        verify(intents, never()).delete(any());
    }

    private ProfileImageUploadIntent image(Instant createdAt) {
        var image = ProfileImageUploadIntent.create(1L, "jpg", createdAt, Duration.ofMinutes(2));
        image.recordUrl("https://bucket.example/" + image.getS3Key());
        return image;
    }
    private ProfileImageUploadIntent orphan() {
        var image = image(NOW.minus(Duration.ofDays(15)));
        image.orphaned(NOW.minus(Duration.ofDays(14)));
        return image;
    }
}
