package com.typenull.pingdom.community.application;

import com.typenull.pingdom.community.api.dto.CommunityImageUploadResponse;
import com.typenull.pingdom.community.domain.*;
import com.typenull.pingdom.community.domain.exception.*;
import com.typenull.pingdom.community.infrastructure.persistence.*;
import com.typenull.pingdom.identity.application.command.ProfileImageFileValidator;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CommunityPostImageService {
    private final CommunityPostImageRepository images;
    private final CommunityPostRepository posts;
    private final S3ObjectStorage storage;
    private final S3ObjectDeleteOutboxPublisher deletes;
    private final ProfileImageFileValidator validator;
    private final Clock clock;
    private final TransactionTemplate transaction;
    public CommunityPostImageService(CommunityPostImageRepository images, CommunityPostRepository posts,
            S3ObjectStorage storage, S3ObjectDeleteOutboxPublisher deletes,
            ProfileImageFileValidator validator, Clock clock, PlatformTransactionManager manager) {
        this.images = images; this.posts = posts; this.storage = storage; this.deletes = deletes;
        this.validator = validator; this.clock = clock; this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public CommunityImageUploadResponse upload(long userId, MultipartFile file) {
        ProfileImageFileValidator.ValidatedProfileImage validated;
        try { validated = validator.validate(file); }
        catch (UsersException ex) { throw new CommunityException(CommunityErrorCode.IMAGE_FILE_INVALID); }
        String key = "community/" + userId + "/" + UUID.randomUUID() + "." + validated.extension();
        // S3 요청 전에 별도 DB 트랜잭션으로 key를 남겨 업로드 중 프로세스 종료도 만료 정리합니다.
        CommunityPostImage pending = transaction.execute(status -> images.saveAndFlush(
                CommunityPostImage.pending(userId, key, LocalDateTime.now(clock).plusHours(24))));
        S3ObjectStorage.S3PutResult result;
        try { result = storage.putAtKey(validated.bytes(), validated.contentType(), key); }
        catch (S3ObjectStorage.S3StorageException ex) {
            throw new CommunityException(CommunityErrorCode.IMAGE_UPLOAD_UNAVAILABLE);
        }
        return transaction.execute(status -> {
            CommunityPostImage image = images.findAllByIdForUpdate(List.of(pending.getId())).get(0);
            image.uploaded(result.url());
            return new CommunityImageUploadResponse(image.getId(), image.getImageUrl(), image.getExpiresAt());
        });
    }

    @Transactional
    public void attach(long userId, long postId, List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) return;
        if (requestedIds.size() > 10 || new HashSet<>(requestedIds).size() != requestedIds.size()
                || requestedIds.stream().anyMatch(Objects::isNull)) invalid();
        List<CommunityPostImage> found = images.findAllByIdForUpdate(requestedIds);
        LocalDateTime now = LocalDateTime.now(clock);
        if (found.size() != requestedIds.size()) invalid();
        Map<Long, CommunityPostImage> byId = new HashMap<>();
        for (CommunityPostImage image : found) {
            if (!image.getUserId().equals(userId) || image.getPostId() != null || image.getImageUrl() == null
                    || !image.getExpiresAt().isAfter(now)) invalid();
            byId.put(image.getId(), image);
        }
        for (int position = 0; position < requestedIds.size(); position++)
            byId.get(requestedIds.get(position)).attach(postId, position);
    }

    @Transactional
    public void delete(long userId, long imageId) {
        List<CommunityPostImage> found = images.findAllByIdForUpdate(List.of(imageId));
        if (found.isEmpty() || !found.get(0).getUserId().equals(userId))
            throw new CommunityException(CommunityErrorCode.IMAGE_NOT_FOUND);
        CommunityPostImage image = found.get(0);
        if (image.getPostId() != null) {
            CommunityPost post = posts.findByIdAndHiddenFalse(image.getPostId()).orElseThrow(
                    () -> new CommunityException(CommunityErrorCode.IMAGE_NOT_FOUND));
            if (!post.getUserId().equals(userId)) throw new CommunityException(CommunityErrorCode.IMAGE_NOT_FOUND);
        }
        remove(image, "USER_DELETE");
    }

    @Scheduled(fixedDelayString = "${community.images.cleanup-delay-ms:60000}")
    @Transactional
    public void cleanupExpired() {
        // 행 잠금과 SKIP LOCKED로 게시글 연결과 만료 삭제가 같은 파일을 동시에 처리하지 않습니다.
        for (CommunityPostImage image : images.findExpiredForUpdate(LocalDateTime.now(clock))) remove(image, "UNUSED_EXPIRED");
    }

    private void remove(CommunityPostImage image, String reason) {
        deletes.publish(image.getS3Key(), "COMMUNITY_IMAGE", image.getId().toString(), reason);
        images.delete(image);
    }
    private void invalid() { throw new CommunityException(CommunityErrorCode.INVALID_IMAGE); }
}
