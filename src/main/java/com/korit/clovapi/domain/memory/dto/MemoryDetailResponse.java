package com.korit.clovapi.domain.memory.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.korit.clovapi.domain.memory.entity.Memory;
import com.korit.clovapi.global.dto.UserSummaryResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MemoryDetailResponse(
        String id, String roomId, String planId, UserSummaryResponse writer,
        String title, String content, LocalDate memoryDate,
        List<MemoryImageResponse> images, List<String> tags, List<UserSummaryResponse> participants,
        int commentCount, LocalDateTime createdAt,
        /**
         * 생성(201) 응답에만 실리는 실지급 골드(계약 §10·§15-4). 조회·수정 응답에서는 null 이라
         * 직렬화에서 통째로 빠진다 — 그때는 지급이 일어나지 않으므로 0 을 보내면 "캡에 걸려
         * 0원"과 구분되지 않는다.
         *
         * ★ 0 은 정상값이다. 하루 총 상한 초과 · 자유 추억 횟수 초과 · 본문 20자 미만 ·
         * 삭제 후 재작성(revive)에서 0 이 나간다. 에러로 만들지 않는다 — 화면은 이 값이
         * 0보다 클 때만 골드 획득 연출을 한다(§12 마스코트 교감과 같은 규약).
         */
        @JsonInclude(JsonInclude.Include.NON_NULL) Long earnedGold
) {
    public static MemoryDetailResponse from(Memory memory, List<MemoryImageResponse> images, List<String> tags,
                                            List<UserSummaryResponse> participants) {
        return new MemoryDetailResponse(
                String.valueOf(memory.getId()),
                String.valueOf(memory.getRoomId()),
                memory.getPlanId() == null ? null : String.valueOf(memory.getPlanId()),
                new UserSummaryResponse(String.valueOf(memory.getWriterId()), memory.getWriterNickname(),
                        memory.getWriterProfileImageUrl()),
                memory.getTitle(), memory.getContent(), memory.getMemoryDate(),
                images, tags, participants,
                memory.getCommentCount() == null ? 0 : memory.getCommentCount(), memory.getCreatedAt(),
                null);
    }

    /** 생성 응답용 — 조회로 만든 상세에 실지급액만 얹는다. */
    public MemoryDetailResponse withEarnedGold(long earned) {
        return new MemoryDetailResponse(id, roomId, planId, writer, title, content, memoryDate,
                images, tags, participants, commentCount, createdAt, earned);
    }
}
