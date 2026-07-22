package com.korit.clovapi.domain.memory.mapper;

public class ParticipantRow {

    private Long memoryId; // 배치 조회(findParticipantsByMemoryIds)에서만 채워짐 — 그룹핑 키.
    private Long userId;
    private String nickname;
    private String profileImageUrl;

    public Long getMemoryId() { return memoryId; }
    public void setMemoryId(Long memoryId) { this.memoryId = memoryId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
}
