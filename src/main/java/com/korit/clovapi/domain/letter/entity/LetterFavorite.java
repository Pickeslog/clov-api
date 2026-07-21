package com.korit.clovapi.domain.letter.entity;

import java.time.LocalDateTime;

public class LetterFavorite {

    private Long letterId;
    private Long userId;
    private LocalDateTime createdAt;

    public Long getLetterId() { return letterId; }
    public void setLetterId(Long letterId) { this.letterId = letterId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
