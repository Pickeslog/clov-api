package com.korit.clovapi.domain.memory.mapper;

import com.korit.clovapi.domain.memory.dto.UpdateMemoryRequest;
import com.korit.clovapi.domain.memory.entity.Memory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface MemoryMapper {

    void insert(Memory memory);

    Optional<Memory> findById(@Param("memoryId") long memoryId);

    /** 삭제분 포함(clov-api#98) — soft delete가 uk_memories_plan_writer를 비우지 않아서, 삭제된
     * 행도 여기서 봐야 재작성 시 "되살릴지 막을지"를 판단할 수 있다. 활성 여부는 호출부가
     * {@code deletedAt}으로 가른다. */
    Optional<Memory> findByPlanIdAndWriterId(@Param("planId") long planId, @Param("writerId") long writerId);

    Optional<Long> findPlanRoomId(@Param("planId") long planId);

    Optional<String> findPlanMemoryStatus(@Param("planId") long planId);

    void updatePlanMemoryStatusWritten(@Param("planId") long planId);

    /** 약속 상태를 임의 값으로(clov-api#98 — 삭제/해제 시 CANDIDATE로 되돌리는 용도). */
    void updatePlanMemoryStatus(@Param("planId") long planId, @Param("status") String status);

    /** 그 약속에 살아 있는(soft delete 안 된) 추억이 몇 개 남았는지 — 다른 멤버 기록이 남아 있으면
     * memory_status를 되돌리면 안 되므로(핵심 원칙: 친구별로 각자 기록) 이 카운트로 판단한다. */
    int countActiveByPlanId(@Param("planId") long planId);

    /** 소프트 삭제된 행을 되살려 새 내용으로 덮어쓴다(clov-api#98 — 삭제 후 같은 약속 재작성).
     * created_at도 지금 시각으로 새로 찍는다 — 사용자 입장에서는 완전히 새로 쓴 기록이지, 옛날에
     * 만든 행을 "고친" 게 아니다(같은 행을 재사용하는 건 유니크 제약 때문일 뿐인 구현 디테일). */
    void revive(@Param("memoryId") long memoryId, @Param("title") String title,
               @Param("content") String content, @Param("memoryDate") LocalDate memoryDate,
               @Param("createdAt") LocalDateTime createdAt);

    /** 약속 연결 변경/해제(clov-api#98) — planId는 null 허용(해제). */
    void updateMemoryPlanId(@Param("memoryId") long memoryId, @Param("planId") Long planId);

    List<Memory> findFeed(@Param("roomId") long roomId, @Param("month") String month,
                          @Param("writerId") Long writerId, @Param("tag") String tag,
                          @Param("participantUserId") Long participantUserId,
                          @Param("limit") int limit, @Param("offset") int offset);

    void update(@Param("memoryId") long memoryId, @Param("request") UpdateMemoryRequest request);

    void softDelete(@Param("memoryId") long memoryId, @Param("deletedAt") LocalDateTime deletedAt);

    void insertTags(@Param("memoryId") long memoryId, @Param("tags") List<String> tags);

    void deleteTags(@Param("memoryId") long memoryId);

    List<String> findTags(@Param("memoryId") long memoryId);

    void insertParticipants(@Param("memoryId") long memoryId, @Param("userIds") List<Long> userIds);

    void deleteParticipants(@Param("memoryId") long memoryId);

    List<ParticipantRow> findParticipants(@Param("memoryId") long memoryId);

    /** 여러 추억의 참여자를 한 번에 조회 — 피드 카드용(N+1 회피). row.memoryId로 그룹핑. */
    List<ParticipantRow> findParticipantsByMemoryIds(@Param("memoryIds") List<Long> memoryIds);
}
