package com.korit.clovapi.domain.memory.dto;

import java.util.List;

public record MemoryFeedResponse(List<MemorySummaryResponse> items) {
}
