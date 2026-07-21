package com.korit.clovapi.domain.plan.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Plan {

    private Long id;
    private Long roomId;
    private Long writerId;
    private String writerNickname;
    private String writerProfileImageUrl;
    private String title;
    private String description;
    private String status;
    private String memoryStatus;
    private LocalDate planDate;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Long getWriterId() { return writerId; }
    public void setWriterId(Long writerId) { this.writerId = writerId; }
    public String getWriterNickname() { return writerNickname; }
    public void setWriterNickname(String writerNickname) { this.writerNickname = writerNickname; }
    public String getWriterProfileImageUrl() { return writerProfileImageUrl; }
    public void setWriterProfileImageUrl(String writerProfileImageUrl) { this.writerProfileImageUrl = writerProfileImageUrl; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMemoryStatus() { return memoryStatus; }
    public void setMemoryStatus(String memoryStatus) { this.memoryStatus = memoryStatus; }
    public LocalDate getPlanDate() { return planDate; }
    public void setPlanDate(LocalDate planDate) { this.planDate = planDate; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
