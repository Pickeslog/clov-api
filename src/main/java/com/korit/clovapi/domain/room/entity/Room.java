package com.korit.clovapi.domain.room.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Room {

    private Long id;
    private String name;
    private String description;
    private String themeColor;
    private String transportType;
    private String coverPhotoUrl;
    private String coverTitle;
    private Integer friendshipLevel;
    private Integer expPoint;
    private String status;
    private Integer memberCount;
    private Boolean favorite;
    private String myStatusMessage;
    private LocalDateTime scheduledDeleteAt;
    private LocalDateTime createdAt;
    // 요약 전용(다음 예정 약속) — 목록 쿼리 서브쿼리로 채워짐.
    private String nextPlanTitle;
    private LocalDate nextPlanDate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getThemeColor() { return themeColor; }
    public void setThemeColor(String themeColor) { this.themeColor = themeColor; }
    public String getTransportType() { return transportType; }
    public void setTransportType(String transportType) { this.transportType = transportType; }
    public String getCoverPhotoUrl() { return coverPhotoUrl; }
    public void setCoverPhotoUrl(String coverPhotoUrl) { this.coverPhotoUrl = coverPhotoUrl; }
    public String getCoverTitle() { return coverTitle; }
    public void setCoverTitle(String coverTitle) { this.coverTitle = coverTitle; }
    public Integer getFriendshipLevel() { return friendshipLevel; }
    public void setFriendshipLevel(Integer friendshipLevel) { this.friendshipLevel = friendshipLevel; }
    public Integer getExpPoint() { return expPoint; }
    public void setExpPoint(Integer expPoint) { this.expPoint = expPoint; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
    public Boolean getFavorite() { return favorite; }
    public void setFavorite(Boolean favorite) { this.favorite = favorite; }
    public String getMyStatusMessage() { return myStatusMessage; }
    public void setMyStatusMessage(String myStatusMessage) { this.myStatusMessage = myStatusMessage; }
    public LocalDateTime getScheduledDeleteAt() { return scheduledDeleteAt; }
    public void setScheduledDeleteAt(LocalDateTime scheduledDeleteAt) { this.scheduledDeleteAt = scheduledDeleteAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getNextPlanTitle() { return nextPlanTitle; }
    public void setNextPlanTitle(String nextPlanTitle) { this.nextPlanTitle = nextPlanTitle; }
    public LocalDate getNextPlanDate() { return nextPlanDate; }
    public void setNextPlanDate(LocalDate nextPlanDate) { this.nextPlanDate = nextPlanDate; }
}
