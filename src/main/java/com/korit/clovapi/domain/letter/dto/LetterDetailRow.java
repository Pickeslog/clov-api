package com.korit.clovapi.domain.letter.dto;

import java.time.LocalDateTime;

/**
 * Flat projection populated by LetterMapper.xml (sender/receiver join + viewer favorite flag).
 * Not exposed directly in API responses; LetterService maps it to LetterResponse.
 */
public class LetterDetailRow {

    private Long id;
    private Long senderId;
    private String senderNickname;
    private String senderProfileImageUrl;
    private Long receiverId;
    private String receiverNickname;
    private String receiverProfileImageUrl;
    private String content;
    private String emoji;
    private LocalDateTime readAt;
    private LocalDateTime sentAt;
    private boolean favorite;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public String getSenderNickname() { return senderNickname; }
    public void setSenderNickname(String senderNickname) { this.senderNickname = senderNickname; }
    public String getSenderProfileImageUrl() { return senderProfileImageUrl; }
    public void setSenderProfileImageUrl(String senderProfileImageUrl) { this.senderProfileImageUrl = senderProfileImageUrl; }
    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }
    public String getReceiverNickname() { return receiverNickname; }
    public void setReceiverNickname(String receiverNickname) { this.receiverNickname = receiverNickname; }
    public String getReceiverProfileImageUrl() { return receiverProfileImageUrl; }
    public void setReceiverProfileImageUrl(String receiverProfileImageUrl) { this.receiverProfileImageUrl = receiverProfileImageUrl; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
}
