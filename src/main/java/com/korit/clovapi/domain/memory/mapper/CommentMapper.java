package com.korit.clovapi.domain.memory.mapper;

import com.korit.clovapi.domain.memory.entity.MemoryComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CommentMapper {

    void insert(MemoryComment comment);

    Optional<MemoryComment> findById(@Param("commentId") long commentId);

    /** 한 추억당 작성자 1인 1개(계약 §10) — 중복 작성 차단용 조회. */
    Optional<MemoryComment> findByMemoryIdAndWriterId(@Param("memoryId") long memoryId,
                                                     @Param("writerId") long writerId);

    List<MemoryComment> findByMemoryId(@Param("memoryId") long memoryId);

    void update(@Param("commentId") long commentId, @Param("content") String content);

    void delete(@Param("commentId") long commentId);

    /** clov-api#98 — 소프트 삭제된 추억을 되살릴 때, 삭제 전 친구 메시지는 새 내용에 이어받지 않는다.
     * 완전히 다른 내용으로 다시 쓴 추억에 옛 댓글이 그대로 붙어 있으면 맥락이 안 맞는다. */
    void deleteByMemoryId(@Param("memoryId") long memoryId);
}
