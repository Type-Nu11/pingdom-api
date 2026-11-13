package com.typenull.pingdom.identity.domain;

import jakarta.persistence.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 서버가 만든 전용 key만 추적하며, 삭제 후에도 늦은 PUT을 재확인할 수 있도록 원장을 유지합니다. */
@Entity
@Getter
@Table(name = "profile_image_upload_intent")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileImageUploadIntent {
    public static final Duration ORPHAN_RETENTION = Duration.ofDays(14);
    private static final String PREFIX = "users/profile-images/tracked/";
    private static final Pattern RESERVED_PATH = Pattern.compile("(?:^|/)users/profile-images/tracked(?:/|$)");

    @Id @Column(length = 36) private String id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "s3_key", nullable = false, unique = true, length = 512) private String s3Key;
    @Column(name = "image_url", nullable = false, unique = true, length = 2048) private String imageUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private State state;
    @Column(name = "upload_deadline", nullable = false) private Instant uploadDeadline;
    @Column(name = "orphaned_at") private Instant orphanedAt;
    @Column(name = "next_check_at", nullable = false) private Instant nextCheckAt;
    @Column(name = "delete_attempt", length = 36) private String deleteAttempt;

    public enum State { UPLOADING, ATTACHED, ORPHAN }

    public static ProfileImageUploadIntent create(Long userId, String extension, Instant now, Duration timeout) {
        if (userId == null || userId <= 0 || !("jpg".equals(extension) || "png".equals(extension)))
            throw new IllegalArgumentException("프로필 업로드 소유자 또는 확장자가 올바르지 않습니다.");
        ProfileImageUploadIntent intent = new ProfileImageUploadIntent();
        intent.id = UUID.randomUUID().toString();
        intent.userId = userId;
        intent.s3Key = PREFIX + intent.id + "." + extension;
        intent.state = State.UPLOADING;
        intent.uploadDeadline = now.plus(timeout).plus(Duration.ofMinutes(1));
        intent.nextCheckAt = intent.uploadDeadline;
        return intent;
    }

    public void recordUrl(String imageUrl) {
        if (this.imageUrl != null || imageUrl == null || imageUrl.isBlank())
            throw new IllegalArgumentException("프로필 업로드 URL이 올바르지 않습니다.");
        this.imageUrl = imageUrl;
    }

    public boolean canComplete(Long userId, Instant now) {
        return state == State.UPLOADING && this.userId.equals(userId) && ownsKey() && uploadDeadline.isAfter(now);
    }

    public boolean ownsKey() {
        return id != null && s3Key != null && s3Key.matches(Pattern.quote(PREFIX + id) + "\\.(jpg|png)");
    }

    /** 발급 host에 의존하지 않아 CDN·S3 virtual-host/path-style 별칭도 같은 예약 namespace로 취급합니다. */
    public static boolean isReservedProfileUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            // URL 소비자가 정규화하는 공백·구분자와 잘못된 query가 예약 path 검사를 우회하지 못하게 합니다.
            String input = value.strip().trim().replace("\r", "").replace("\n", "").replace("\t", "").replace('\\', '/');
            URI parsed = new URI(input.split("[?#]", 2)[0]);
            String decodedPath = parsed.getPath();
            if (decodedPath == null && ("https".equalsIgnoreCase(parsed.getScheme()) || "http".equalsIgnoreCase(parsed.getScheme())))
                decodedPath = parsed.getSchemeSpecificPart();
            if (decodedPath == null) return false;
            // 인코딩된 구분자와 dot segment도 정규화해 가입 입력으로 소유 key를 다시 채택하지 못하게 합니다.
            String normalizedPath = new URI(null, null, decodedPath, null).normalize().getPath();
            return RESERVED_PATH.matcher(normalizedPath).find();
        } catch (URISyntaxException exception) {
            // 기존의 외부 URL 입력 검증 정책은 바꾸지 않습니다. 잘못된 URI는 서버 발급 URL과 같을 수 없습니다.
            return false;
        }
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
        return state == State.ORPHAN && orphanedAt != null && !orphanedAt.plus(ORPHAN_RETENTION).isAfter(now);
    }

    public String queueDeletion(Instant now, Duration recheckDelay) {
        deleteAttempt = UUID.randomUUID().toString();
        nextCheckAt = now.plus(recheckDelay);
        return deleteAttempt;
    }

    public boolean matchesAttempt(String attempt) { return attempt != null && attempt.equals(deleteAttempt); }

    public void recheckAt(Instant when) { nextCheckAt = when; }
}
