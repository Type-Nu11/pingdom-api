package com.typenull.pingdom.domain.admin.service;

import com.typenull.pingdom.domain.admin.dto.post.AdminPostResponse;
import com.typenull.pingdom.domain.admin.enums.SortParam;

import java.util.List;

public interface AdminPostQueryService {
    AdminPostResponse listPosts(int limit, int page, SortParam sortParam);
}

