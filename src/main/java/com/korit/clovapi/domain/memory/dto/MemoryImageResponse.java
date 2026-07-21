package com.korit.clovapi.domain.memory.dto;

// shape only; image upload (R2) never populates rows, so this list is always empty in R1
public record MemoryImageResponse(String id, String imageUrl, Integer sortOrder) {
}
