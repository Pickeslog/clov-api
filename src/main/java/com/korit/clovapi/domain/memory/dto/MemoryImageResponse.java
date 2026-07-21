package com.korit.clovapi.domain.memory.dto;

import com.korit.clovapi.domain.memory.entity.MemoryImage;

public record MemoryImageResponse(String id, String imageUrl, Integer sortOrder) {
    public static MemoryImageResponse from(MemoryImage image) {
        return new MemoryImageResponse(String.valueOf(image.getId()), image.getImageUrl(), image.getSortOrder());
    }
}
