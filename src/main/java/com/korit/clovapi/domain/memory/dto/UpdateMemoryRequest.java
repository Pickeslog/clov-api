package com.korit.clovapi.domain.memory.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UpdateMemoryRequest {

    @Size(max = 25)
    private String title;
    @Size(max = 100)
    private String content;
    private LocalDate memoryDate;
    private List<@Size(max = 50) String> tags;
    private List<String> participantUserIds;
    @JsonIgnore
    private final Set<String> providedFields = new HashSet<>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; providedFields.add("title"); }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; providedFields.add("content"); }
    public LocalDate getMemoryDate() { return memoryDate; }
    public void setMemoryDate(LocalDate memoryDate) { this.memoryDate = memoryDate; providedFields.add("memoryDate"); }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; providedFields.add("tags"); }
    public List<String> getParticipantUserIds() { return participantUserIds; }
    public void setParticipantUserIds(List<String> participantUserIds) {
        this.participantUserIds = participantUserIds;
        providedFields.add("participantUserIds");
    }
    public boolean has(String field) { return providedFields.contains(field); }

    @JsonIgnore
    @AssertTrue(message = "At least one memory field is required")
    public boolean hasProvidedFields() { return !providedFields.isEmpty(); }
}
