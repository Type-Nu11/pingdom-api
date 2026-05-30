package com.typenull.pingdom.domain.posts.repository;

import com.typenull.pingdom.domain.posts.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
}

