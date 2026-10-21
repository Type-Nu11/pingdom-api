package com.typenull.pingdom.moderation.application.query.place.map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** 표시 상한의 초기값은 200이며 데이터 측정 후 설정으로 조정한다. */
@Validated
@ConfigurationProperties("admin.place-map")
public record AdminPlaceMapProperties(@DefaultValue("200") @Min(4) @Max(2000) int maxItems) {}
