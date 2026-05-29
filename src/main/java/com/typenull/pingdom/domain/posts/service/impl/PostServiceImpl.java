package com.typenull.pingdom.domain.posts.service.impl;

import com.typenull.pingdom.domain.posts.domain.Post;
import com.typenull.pingdom.domain.posts.dto.PostUploadRequest;
import com.typenull.pingdom.domain.posts.dto.PostUploadResponse;
import com.typenull.pingdom.domain.posts.repository.PostRepository;
import com.typenull.pingdom.domain.posts.service.PostService;
import com.typenull.pingdom.global.s3.S3ObjectStorage;
import com.typenull.pingdom.global.s3.S3ObjectStorage.S3StorageException;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final S3ObjectStorage s3ObjectStorage;
    private final PostRepository postRepository;

    @Override
    @Transactional
    public PostUploadResponse upload(PostUploadRequest request) throws IOException {
        S3ObjectStorage.S3PutResult putResult;
        try {
            putResult = s3ObjectStorage.put(request.file(), "posts");
        } catch (S3StorageException exception) {
            throw new IllegalStateException("S3 업로드에 실패했습니다.", exception);
        }

        Post saved = postRepository.save(Post.builder()
                .url(putResult.url())
                .s3Key(putResult.key())
                .createdAt(LocalDateTime.now())
                .build());

        return new PostUploadResponse(saved.getId(), saved.getUrl(), saved.getS3Key());
    }
}
