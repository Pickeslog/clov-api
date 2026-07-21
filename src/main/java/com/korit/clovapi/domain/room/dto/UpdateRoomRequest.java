package com.korit.clovapi.domain.room.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.Set;

public class UpdateRoomRequest {

    @Size(max = 100)
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
    public void setName(String name) { this.name = name; providedFields.add("name"); }
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
