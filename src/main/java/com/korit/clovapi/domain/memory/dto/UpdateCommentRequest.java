package com.korit.clovapi.domain.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 한 줄 메시지 수정(계약 §10 PATCH /comments/{commentId}) — 본문 규칙은 작성과 동일. */
public record UpdateCommentRequest(@NotBlank @Size(max = 255) String content) {
}
