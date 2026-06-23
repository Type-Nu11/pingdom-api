package com.typenull.pingdom.post.application.port;

import org.springframework.web.multipart.MultipartFile;

public interface PostImageStorage {

    PostImageUploadResult upload(MultipartFile file);

    void delete(String key);

    enum PostImageStorageError {
        NOT_CONFIGURED,
        STORAGE_ERROR,
        CONNECTION_ERROR,
        IO_ERROR
    }

    record PostImageUploadResult(String key, String url) {
    }

    class PostImageStorageException extends RuntimeException {
        private final PostImageStorageError error;

        public PostImageStorageException(PostImageStorageError error, String message, Throwable cause) {
            super(message, cause);
            this.error = error;
        }

        public PostImageStorageError getError() {
            return error;
        }
    }
}
