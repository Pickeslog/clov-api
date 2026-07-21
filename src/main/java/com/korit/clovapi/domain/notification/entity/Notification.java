package com.korit.clovapi.domain.notification.entity;

import java.time.LocalDateTime;

public class Notification {
    private Long id;
    private Long roomId;
    private Long recipientId;
    private Long actorId;
    private String actorNickname;
    private String actorProfileImageUrl;
    private String type;
    private Long referenceId;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    
    public Long getRecipientId() { return recipientId; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }
    
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    
    public String getActorNickname() { return actorNickname; }
    public void setActorNickname(String actorNickname) { this.actorNickname = actorNickname; }
    
    public String getActorProfileImageUrl() { return actorProfileImageUrl; }
    public void setActorProfileImageUrl(String actorProfileImageUrl) { this.actorProfileImageUrl = actorProfileImageUrl; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }
    
    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
