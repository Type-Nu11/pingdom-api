package com.typenull.pingdom.analysis.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 프론트가 전달하는 입지 분석 조건이다. 지역은 필수이며 나머지 조건은 AI 입력으로 사용한다. */
public class LocationAnalysisRequest {

    @Schema(description = "희망 지역", example = "대구광역시 북구 서변동", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "지역은 필수입니다.")
    private String region;

    @Schema(description = "가게 업종 또는 카테고리", example = "카페", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "가게 업종은 필수입니다.")
    private String category;

    @Schema(description = "주요 고객층(연령·성별)", example = "20~30대 여성")
    private String targetCustomerGroup;

    @Schema(description = "주요 영업 시간대", example = "평일 09:00~22:00")
    private String operatingHours;

    @Schema(description = "보고서 보관을 위한 이메일", example = "owner@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @Schema(description = "개인정보 수집·이용 동의 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "개인정보 동의 여부는 필수입니다.")
    @AssertTrue(message = "보고서 보관을 위해 개인정보 수집·이용에 동의해야 합니다.")
    private Boolean privacyConsent;

    @JsonIgnore
    private final Map<String, Object> additionalCriteria = new LinkedHashMap<>();

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTargetCustomerGroup() {
        return targetCustomerGroup;
    }

    public void setTargetCustomerGroup(String targetCustomerGroup) {
        this.targetCustomerGroup = targetCustomerGroup;
    }

    public String getOperatingHours() {
        return operatingHours;
    }

    public void setOperatingHours(String operatingHours) {
        this.operatingHours = operatingHours;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getPrivacyConsent() {
        return privacyConsent;
    }

    public void setPrivacyConsent(Boolean privacyConsent) {
        this.privacyConsent = privacyConsent;
    }

    @JsonAnySetter
    public void addAdditionalCriterion(String name, Object value) {
        additionalCriteria.put(name, value);
    }

    public Map<String, Object> additionalCriteria() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(additionalCriteria));
    }

    public Map<String, Object> toCriteriaMap() {
        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("region", region);
        criteria.put("category", category);
        criteria.put("targetCustomerGroup", targetCustomerGroup);
        criteria.put("operatingHours", operatingHours);
        criteria.put("additionalCriteria", additionalCriteria());
        return criteria;
    }
}
