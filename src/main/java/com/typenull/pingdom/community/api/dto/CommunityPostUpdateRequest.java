package com.typenull.pingdom.community.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;

/** PATCH의 누락과 명시적인 null을 구분하며 기존 작성 제약을 유지한다. */
@Getter
@Schema(description = "본인 공개 게시글 부분 수정. 누락은 유지, countryCode/region의 null만 초기화하며 나머지 null은 400")
public class CommunityPostUpdateRequest {
    @Size(max = 30) @Pattern(regexp = "(?s).*\\S.*")
    @Schema(description = "단일 카테고리 ID. 누락은 유지", example = "TRAVEL")
    private String categoryId;
    @Size(max = 50) @Pattern(regexp = "(?s).*\\S.*")
    @Schema(description = "제목. 공백만 있는 값 불가, 누락은 유지", example = "수정한 여행 후기")
    private String title;
    @Size(max = 5000) @Pattern(regexp = "(?s).*\\S.*")
    @Schema(description = "본문. 공백만 있는 값 불가, 누락은 유지", example = "수정한 본문")
    private String content;
    @Schema(description = "연결 장소 전체 목록. []는 제거, PLACE 카테고리는 1개 이상 필요", example = "[]")
    private List<@NotNull @Positive Long> placeIds;
    @Size(max = 10)
    @Schema(description = "최종 사진 ID 순서. 같은 글의 기존 사진과 본인 미사용 업로드만 허용. []는 모두 삭제", example = "[12, 11]")
    private List<@NotNull @Positive Long> imageIds;
    @Pattern(regexp = "^[A-Z]{2}$")
    @Schema(description = "ISO alpha-2 국가 코드. 누락은 유지, null은 초기화", nullable = true, example = "KR")
    private String countryCode;
    @Size(max = 100)
    @Schema(description = "표시 지역. 누락은 유지, null 또는 공백은 초기화", nullable = true, example = "서울")
    private String region;
    @JsonIgnore @Schema(hidden = true)
    private boolean countryCodeSpecified;
    @JsonIgnore @Schema(hidden = true)
    private boolean regionSpecified;

    @JsonSetter(nulls = Nulls.FAIL)
    public void setCategoryId(String value) { categoryId = value; }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setTitle(String value) { title = value; }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setContent(String value) { content = value; }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setPlaceIds(List<Long> value) { placeIds = value; }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setImageIds(List<Long> value) { imageIds = value; }
    @JsonSetter
    public void setCountryCode(String value) { countryCode = value; countryCodeSpecified = true; }
    @JsonSetter
    public void setRegion(String value) { region = value; regionSpecified = true; }

    @AssertTrue(message = "수정할 필드를 하나 이상 지정해야 합니다.")
    @JsonIgnore @Schema(hidden = true)
    public boolean isChangeRequested() {
        return categoryId != null || title != null || content != null || placeIds != null || imageIds != null
                || countryCodeSpecified || regionSpecified;
    }
}
