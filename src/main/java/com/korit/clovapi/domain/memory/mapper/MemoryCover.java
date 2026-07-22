package com.korit.clovapi.domain.memory.mapper;

/** 피드 카드용 대표 이미지 정보 — 대표 URL(최소 sort_order)과 총 이미지 수를 한 번에 담는다. */
public class MemoryCover {

    private Long memoryId;
    private String imageUrl;
    private int imageCount;

    public Long getMemoryId() { return memoryId; }
    public void setMemoryId(Long memoryId) { this.memoryId = memoryId; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public int getImageCount() { return imageCount; }
    public void setImageCount(int imageCount) { this.imageCount = imageCount; }
}
