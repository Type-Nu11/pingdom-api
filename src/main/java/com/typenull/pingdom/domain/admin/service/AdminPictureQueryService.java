package com.typenull.pingdom.domain.admin.service;

import com.typenull.pingdom.domain.admin.dto.picture.AdminPictureResponse;
import java.util.List;

public interface AdminPictureQueryService {
    List<AdminPictureResponse> listPictures(int limit);
}

