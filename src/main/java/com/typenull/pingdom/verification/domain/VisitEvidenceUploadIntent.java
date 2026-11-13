package com.typenull.pingdom.verification.domain;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 업로드 전에 소유 key를 기록하고, 늦은 put도 다시 찾을 수 있도록 삭제 뒤에도 이력을 유지합니다. */
@Entity
@Getter
@Table(name = "visit_evidence_upload_intent")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitEvidenceUploadIntent {
    public static final Duration ORPHAN_RETENTION = Duration.ofDays(14);
    private static final String PREFIX = "visit-evidence/tracked/";

    @Id @Column(length = 36) private String id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "check_in_id", nullable = false) private Long checkInId;
    @Column(name = "s3_key", nullable = false, unique = true, length = 512) private String s3Key;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private State state;
    @Column(name = "upload_deadline", nullable = false) private Instant uploadDeadline;
    @Column(name = "orphaned_at") private Instant orphanedAt;
    @Column(name = "next_check_at", nullable = false) private Instant nextCheckAt;
    @Column(name = "delete_attempt", length = 36) private String deleteAttempt;

    public enum State { UPLOADING, ATTACHED, ORPHAN }

    public static VisitEvidenceUploadIntent create(Long userId, Long checkInId, String extension,
            Instant now, Duration uploadTimeout) {
        if (userId == null || userId <= 0 || checkInId == null || checkInId <= 0
                || !("jpg".equals(extension) || "png".equals(extension))) {
            throw new IllegalArgumentException("증빙 업로드 소유자 또는 확장자가 올바르지 않습니다.");
        }
        VisitEvidenceUploadIntent intent = new VisitEvidenceUploadIntent();
        intent.id = UUID.randomUUID().toString();
        intent.userId = userId;
        intent.checkInId = checkInId;
        intent.s3Key = PREFIX + intent.id + "." + extension;
        intent.state = State.UPLOADING;
        // SDK 호출 상한 뒤 여유를 두고도 완료되지 않은 실행은 더 이상 증빙을 연결할 수 없습니다.
        intent.uploadDeadline = now.plus(uploadTimeout).plus(Duration.ofMinutes(1));
        intent.nextCheckAt = intent.uploadDeadline;
        return intent;
    }

    public boolean canComplete(Long userId, Long checkInId, Instant now) {
        return state == State.UPLOADING && this.userId.equals(userId) && this.checkInId.equals(checkInId)
                && uploadDeadline.isAfter(now) && ownsKey();
    }

    public boolean ownsKey() {
        return s3Key != null && id != null
                && s3Key.matches(Pattern.quote(PREFIX + id) + "\\.(jpg|png)");
    }

    public void attached(Instant now, Duration recheckDelay) {
        state = State.ATTACHED;
        orphanedAt = null;
        deleteAttempt = null;
        nextCheckAt = now.plus(recheckDelay);
    }

    public void orphaned(Instant now) {
        if (state != State.ORPHAN) {
            state = State.ORPHAN;
            orphanedAt = now;
        }
        nextCheckAt = orphanedAt.plus(ORPHAN_RETENTION);
    }

    public boolean deletionDue(Instant now) {
        return state == State.ORPHAN && orphanedAt != null
                && !orphanedAt.plus(ORPHAN_RETENTION).isAfter(now);
    }

    public String queueDeletion(Instant now, Duration recheckDelay) {
        deleteAttempt = UUID.randomUUID().toString();
        nextCheckAt = now.plus(recheckDelay);
        return deleteAttempt;
    }

    public boolean matchesAttempt(String attempt) {
        return attempt != null && attempt.equals(deleteAttempt);
    }

    public void recheckAt(Instant when) { nextCheckAt = when; }
}
