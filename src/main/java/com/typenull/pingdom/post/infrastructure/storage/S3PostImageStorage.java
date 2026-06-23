package com.typenull.pingdom.post.infrastructure.storage;

import com.typenull.pingdom.post.application.port.PostImageStorage;
import com.typenull.pingdom.post.application.port.PostImageStorage.PostImageStorageError;
import com.typenull.pingdom.post.application.port.PostImageStorage.PostImageStorageException;
import com.typenull.pingdom.post.application.port.PostImageStorage.PostImageUploadResult;
import com.typenull.pingdom.shared.storage.s3.S3ObjectStorage;
import com.typenull.pingdom.shared.storage.s3.S3ObjectStorage.S3StorageError;
import com.typenull.pingdom.shared.storage.s3.S3ObjectStorage.S3StorageException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class S3PostImageStorage implements PostImageStorage {

    private static final String POST_IMAGE_KEY_PREFIX = "map";

    private final S3ObjectStorage s3ObjectStorage;

    @Override
    public PostImageUploadResult upload(MultipartFile file) {
        try {
            S3ObjectStorage.S3PutResult result = s3ObjectStorage.put(file, POST_IMAGE_KEY_PREFIX);
            return new PostImageUploadResult(result.key(), result.url());
        } catch (IOException exception) {
            throw new PostImageStorageException(PostImageStorageError.IO_ERROR, "게시글 이미지 파일을 읽을 수 없습니다.", exception);
        } catch (S3StorageException exception) {
            throw toPostImageStorageException(exception);
        }
    }

    @Override
    public void delete(String key) {
        s3ObjectStorage.delete(key);
    }

    private PostImageStorageException toPostImageStorageException(S3StorageException exception) {
        S3StorageError error = exception.getError();
        if (error == S3StorageError.NOT_CONFIGURED) {
            return new PostImageStorageException(PostImageStorageError.NOT_CONFIGURED, exception.getMessage(), exception);
        }
        if (error == S3StorageError.CONNECTION_ERROR) {
            return new PostImageStorageException(PostImageStorageError.CONNECTION_ERROR, exception.getMessage(), exception);
        }
        return new PostImageStorageException(PostImageStorageError.STORAGE_ERROR, exception.getMessage(), exception);
    }
}
