package com.korit.clovapi.domain.memory.entity;

import java.time.LocalDateTime;

public class MemoryComment {

    private Long id;
    private Long memoryId;
    private Long writerId;
    private String writerNickname;
    private String writerProfileImageUrl;
    private String content;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMemoryId() { return memoryId; }
    public void setMemoryId(Long memoryId) { this.memoryId = memoryId; }
    public Long getWriterId() { return writerId; }
    public void setWriterId(Long writerId) { this.writerId = writerId; }
    public String getWriterNickname() { return writerNickname; }
    public void setWriterNickname(String writerNickname) { this.writerNickname = writerNickname; }
    public String getWriterProfileImageUrl() { return writerProfileImageUrl; }
    public void setWriterProfileImageUrl(String writerProfileImageUrl) { this.writerProfileImageUrl = writerProfileImageUrl; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
