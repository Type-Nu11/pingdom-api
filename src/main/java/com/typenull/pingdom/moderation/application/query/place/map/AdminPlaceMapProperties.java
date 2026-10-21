package com.typenull.pingdom.moderation.application.query.place.map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** 전체 마커 표시 한도의 초기값은 100이며 서버 설정으로만 낮출 수 있다. */
@Validated
@ConfigurationProperties("admin.place-map")
public record AdminPlaceMapProperties(@DefaultValue("100") @Min(1) @Max(100) int displayLimit) {}
