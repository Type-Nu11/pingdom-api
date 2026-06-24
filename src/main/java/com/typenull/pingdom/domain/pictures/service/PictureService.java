package com.typenull.pingdom.domain.pictures.service;

import com.typenull.pingdom.domain.pictures.dto.PictureUploadRequest;
import com.typenull.pingdom.domain.pictures.dto.PictureUploadResponse;
import java.io.IOException;

public interface PictureService {
    PictureUploadResponse upload(PictureUploadRequest request) throws IOException;
}

