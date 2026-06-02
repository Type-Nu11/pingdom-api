package com.typenull.pingdom.domain.posts.service;

import com.typenull.pingdom.domain.posts.dto.PostUploadRequest;
import com.typenull.pingdom.domain.posts.dto.PostUploadResponse;
import java.io.IOException;

public interface PostService {
    PostUploadResponse upload(PostUploadRequest request) throws IOException;
}

