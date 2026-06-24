package com.typenull.pingdom.domain.admin.service;

import com.typenull.pingdom.domain.admin.dto.picture.AdminPictureResponse;
import com.typenull.pingdom.domain.admin.enums.SortParam;

import java.util.List;

public interface AdminPictureQueryService {
    AdminPictureResponse listPictures(int limit, int page, SortParam sortParam);
}

