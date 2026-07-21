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

    List<MemoryComment> findByMemoryId(@Param("memoryId") long memoryId);

    void delete(@Param("commentId") long commentId);
}
