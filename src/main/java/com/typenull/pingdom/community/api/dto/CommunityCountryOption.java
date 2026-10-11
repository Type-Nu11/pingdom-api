package com.typenull.pingdom.community.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "국가 필터의 허용 코드와 표시 이름")
public record CommunityCountryOption(String country, String countryName) { }
