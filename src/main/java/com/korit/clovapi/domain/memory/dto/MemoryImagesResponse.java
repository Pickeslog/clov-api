package com.korit.clovapi.domain.memory.dto;

import java.util.List;

/** memory 이미지 목록(순서 재정렬 응답 등). */
public record MemoryImagesResponse(List<MemoryImageResponse> images) {
}
