package com.korit.clovapi.domain.invite.entity;

import java.time.LocalDateTime;

public class RoomJoinRequest {

    private Long id;
    private Long roomId;
    private Long userId;
    private Long inviteId;
    private String status;
    private Long acceptedBy;
    private String nickname;
    private String profileImageUrl;
    private LocalDateTime requestedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime undoDeadlineAt;
    private LocalDateTime rejectedAt;
    private Integer version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getInviteId() { return inviteId; }
    public void setInviteId(Long inviteId) { this.inviteId = inviteId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(Long acceptedBy) { this.acceptedBy = acceptedBy; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
    public LocalDateTime getUndoDeadlineAt() { return undoDeadlineAt; }
    public void setUndoDeadlineAt(LocalDateTime undoDeadlineAt) { this.undoDeadlineAt = undoDeadlineAt; }
    public LocalDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(LocalDateTime rejectedAt) { this.rejectedAt = rejectedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
