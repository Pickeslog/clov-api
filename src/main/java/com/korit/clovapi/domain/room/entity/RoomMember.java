package com.korit.clovapi.domain.room.entity;

import java.time.LocalDateTime;

public class RoomMember {

    private Long id;
    private Long roomId;
    private Long userId;
    private String nickname;
    private String profileImageUrl;
    private String status;
    private Boolean favorite;
    private String statusMessage;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
    private String birthMonthDay;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getFavorite() { return favorite; }
    public void setFavorite(Boolean favorite) { this.favorite = favorite; }
    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
    public LocalDateTime getLeftAt() { return leftAt; }
    public void setLeftAt(LocalDateTime leftAt) { this.leftAt = leftAt; }
    public String getBirthMonthDay() { return birthMonthDay; }
    public void setBirthMonthDay(String birthMonthDay) { this.birthMonthDay = birthMonthDay; }
}
