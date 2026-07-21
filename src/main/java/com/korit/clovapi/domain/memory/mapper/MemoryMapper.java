package com.korit.clovapi.domain.memory.mapper;

import com.korit.clovapi.domain.memory.dto.UpdateMemoryRequest;
import com.korit.clovapi.domain.memory.entity.Memory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface MemoryMapper {

    void insert(Memory memory);

    Optional<Memory> findById(@Param("memoryId") long memoryId);

    Optional<Memory> findByPlanIdAndWriterId(@Param("planId") long planId, @Param("writerId") long writerId);

    Optional<Long> findPlanRoomId(@Param("planId") long planId);

    Optional<String> findPlanMemoryStatus(@Param("planId") long planId);

    void updatePlanMemoryStatusWritten(@Param("planId") long planId);

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
}
