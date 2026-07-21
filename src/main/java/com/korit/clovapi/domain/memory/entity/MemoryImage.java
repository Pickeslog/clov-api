package com.korit.clovapi.domain.memory.entity;

import java.time.LocalDateTime;

public class MemoryImage {

    private Long id;
    private Long memoryId;
    private String imageUrl;
    private Integer sortOrder;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMemoryId() { return memoryId; }
    public void setMemoryId(Long memoryId) { this.memoryId = memoryId; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
