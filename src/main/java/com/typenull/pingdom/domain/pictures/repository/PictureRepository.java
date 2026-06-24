package com.typenull.pingdom.domain.pictures.repository;

import com.typenull.pingdom.domain.pictures.domain.Picture;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PictureRepository extends JpaRepository<Picture, Long> {
}

