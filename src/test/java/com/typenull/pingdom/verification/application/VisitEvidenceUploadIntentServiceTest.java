package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.verification.domain.VisitEvidence;
import com.typenull.pingdom.verification.domain.VisitEvidenceUploadIntent;
import com.typenull.pingdom.verification.infrastructure.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class VisitEvidenceUploadIntentServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");
    private final VisitEvidenceUploadIntentRepository intents = mock(VisitEvidenceUploadIntentRepository.class);
    private final VisitEvidenceRepository evidence = mock(VisitEvidenceRepository.class);
    private final VisitEvidencePersistenceService persistence = mock(VisitEvidencePersistenceService.class);
    private final OutboxEventPublisher publisher = mock(OutboxEventPublisher.class);
    private final VisitEvidenceOrphanProperties properties = new VisitEvidenceOrphanProperties(null, null, null);
    private final VisitEvidenceUploadIntentService service = new VisitEvidenceUploadIntentService(
            intents, evidence, persistence, publisher, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void registerBeforeUploadAndKeepActiveReferenceAfterUnknownCommit() {
        when(intents.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var prepared = service.prepare(1L, 2L, "jpg");
        assertThat(prepared.key()).contains(prepared.intentId());
        var intent = orphan();
        when(intents.findForUpdate(intent.getId())).thenReturn(Optional.of(intent));
        when(evidence.existsByS3Key(intent.getS3Key())).thenReturn(true);
        service.abandon(intent.getId());
        assertThat(intent.getState()).isEqualTo(VisitEvidenceUploadIntent.State.ATTACHED);
    }

    @Test
    void expiredOrOrphanedUploadCannotAttach() {
        var intent = orphan();
        when(intents.findForUpdate(intent.getId())).thenReturn(Optional.of(intent));
        assertThatThrownBy(() -> service.complete(intent.getId(), 1L, 2L, "a.jpg", "image/jpeg", 1, NOW, NOW.plusSeconds(1)))
                .isInstanceOf(com.typenull.pingdom.verification.domain.exception.VisitorVerificationException.class);
        verifyNoInteractions(persistence);
    }

    @Test
    void liveUploadAttachesEvidenceInIntentTransaction() {
        var intent = VisitEvidenceUploadIntent.create(1L, 2L, "jpg", NOW, Duration.ofMinutes(2));
        when(intents.findForUpdate(intent.getId())).thenReturn(Optional.of(intent));
        var saved = VisitEvidence.create(2L, 1L, intent.getS3Key(), "a.jpg", "image/jpeg", 1,
                NOW, NOW.plus(Duration.ofDays(30)));
        when(persistence.save(1L, 2L, intent.getS3Key(), "a.jpg", "image/jpeg", 1, NOW, saved.getExpiresAt()))
                .thenReturn(saved);
        assertThat(service.complete(intent.getId(), 1L, 2L, "a.jpg", "image/jpeg", 1, NOW, saved.getExpiresAt()))
                .isSameAs(saved);
        assertThat(intent.getState()).isEqualTo(VisitEvidenceUploadIntent.State.ATTACHED);
    }

    @Test
    void expiryDuringDatabaseSaveRejectsCompletion() {
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, NOW.plus(Duration.ofMinutes(3)));
        var timedService = new VisitEvidenceUploadIntentService(intents, evidence, persistence, publisher, properties, clock);
        var intent = VisitEvidenceUploadIntent.create(1L, 2L, "jpg", NOW, Duration.ofMinutes(2));
        when(intents.findForUpdate(intent.getId())).thenReturn(Optional.of(intent));
        assertThatThrownBy(() -> timedService.complete(intent.getId(), 1L, 2L, "a.jpg", "image/jpeg", 1,
                NOW, NOW.plus(Duration.ofDays(30))))
                .isInstanceOf(com.typenull.pingdom.verification.domain.exception.VisitorVerificationException.class);
        verify(persistence).save(1L, 2L, intent.getS3Key(), "a.jpg", "image/jpeg", 1,
                NOW, NOW.plus(Duration.ofDays(30)));
        assertThat(intent.getState()).isEqualTo(VisitEvidenceUploadIntent.State.UPLOADING);
    }

    @Test
    void queueOnlyAfterFourteenDaysAndRecheckActiveReference() {
        var intent = orphan();
        when(intents.findDueForUpdate(NOW, 50)).thenReturn(List.of(intent));
        assertThat(service.queueDueCleanup()).isEqualTo(1);
        String attempt = intent.getDeleteAttempt();
        verify(publisher).publish(eq("VISIT_EVIDENCE_ORPHAN_DELETE:" + intent.getId() + ":" + attempt),
                eq(OutboxEventType.VISIT_EVIDENCE_ORPHAN_DELETE_REQUESTED), any(), eq("VISIT_EVIDENCE_UPLOAD"), eq(intent.getId()));
        when(intents.findForUpdate(intent.getId())).thenReturn(Optional.of(intent));
        when(evidence.existsByS3Key(intent.getS3Key())).thenReturn(true);
        assertThat(service.deletionKey(new VisitEvidenceUploadIntentService.DeleteRequest(intent.getId(), attempt))).isNull();
    }

    @Test
    void activeUploadAndYoungOrphanDoNotQueueDeletion() {
        var uploading = VisitEvidenceUploadIntent.create(1L, 2L, "jpg", NOW, Duration.ofMinutes(2));
        var young = VisitEvidenceUploadIntent.create(1L, 2L, "jpg", NOW.minusSeconds(60), Duration.ofMinutes(2));
        young.orphaned(NOW.minus(Duration.ofDays(14)).plusSeconds(1));
        when(intents.findDueForUpdate(NOW, 50)).thenReturn(List.of(uploading, young));
        assertThat(service.queueDueCleanup()).isZero();
        verifyNoInteractions(publisher);
        assertThat(uploading.getState()).isEqualTo(VisitEvidenceUploadIntent.State.UPLOADING);
    }

    @Test
    void expiredUploadStartsRetentionInsteadOfDeletingImmediately() {
        var expired = VisitEvidenceUploadIntent.create(1L, 2L, "jpg", NOW.minus(Duration.ofDays(20)), Duration.ofMinutes(2));
        when(intents.findDueForUpdate(NOW, 50)).thenReturn(List.of(expired));
        assertThat(service.queueDueCleanup()).isZero();
        assertThat(expired.getOrphanedAt()).isEqualTo(NOW);
        verifyNoInteractions(publisher);
    }

    @Test
    void nextAttemptRejectsOldEventButKeepsTombstone() {
        var intent = orphan();
        String old = intent.queueDeletion(NOW, Duration.ofDays(1));
        String current = intent.queueDeletion(NOW.plus(Duration.ofDays(1)), Duration.ofDays(1));
        when(intents.findForUpdate(intent.getId())).thenReturn(Optional.of(intent));
        assertThat(service.deletionKey(new VisitEvidenceUploadIntentService.DeleteRequest(intent.getId(), old))).isNull();
        assertThat(service.deletionKey(new VisitEvidenceUploadIntentService.DeleteRequest(intent.getId(), current))).isEqualTo(intent.getS3Key());
        verify(intents, never()).delete(any());
    }

    private VisitEvidenceUploadIntent orphan() {
        var intent = VisitEvidenceUploadIntent.create(1L, 2L, "jpg", NOW.minus(Duration.ofDays(15)), Duration.ofMinutes(2));
        intent.orphaned(NOW.minus(Duration.ofDays(14)));
        return intent;
    }
}
