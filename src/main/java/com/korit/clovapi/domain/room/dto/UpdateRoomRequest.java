package com.korit.clovapi.domain.room.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.Set;

public class UpdateRoomRequest {

    // 계약 §6: 앞뒤 공백 제거 후 2~20자. 문자 종류는 제한하지 않는다("제주 가자!"·이모지 허용).
    // setter에서 trim하므로 @Size는 trim된 값 기준으로 검증되고, 저장되는 값도 trim된 값이다.
    @Size(min = 2, max = 20)
    private String name;
    @Size(max = 60)
    private String description;
    @Size(max = 20)
    private String themeColor;
    @Size(max = 20)
    private String transportType;
    @Size(max = 512)
    private String coverPhotoUrl;
    @Size(max = 100)
    private String coverTitle;
    @JsonIgnore
    private final Set<String> providedFields = new HashSet<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name == null ? null : name.trim(); providedFields.add("name"); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; providedFields.add("description"); }
    public String getThemeColor() { return themeColor; }
    public void setThemeColor(String themeColor) { this.themeColor = themeColor; providedFields.add("themeColor"); }
    public String getTransportType() { return transportType; }
    public void setTransportType(String transportType) { this.transportType = transportType; providedFields.add("transportType"); }
    public String getCoverPhotoUrl() { return coverPhotoUrl; }
    public void setCoverPhotoUrl(String coverPhotoUrl) { this.coverPhotoUrl = coverPhotoUrl; providedFields.add("coverPhotoUrl"); }
    public String getCoverTitle() { return coverTitle; }
    public void setCoverTitle(String coverTitle) { this.coverTitle = coverTitle; providedFields.add("coverTitle"); }
    public boolean has(String field) { return providedFields.contains(field); }

    @JsonIgnore
    @AssertTrue(message = "At least one room field is required")
    public boolean hasProvidedFields() { return !providedFields.isEmpty(); }
}
