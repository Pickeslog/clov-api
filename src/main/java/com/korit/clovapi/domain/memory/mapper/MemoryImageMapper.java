package com.korit.clovapi.domain.memory.mapper;

import com.korit.clovapi.domain.memory.entity.MemoryImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface MemoryImageMapper {

    void insert(MemoryImage image);

    Optional<MemoryImage> findById(@Param("imageId") long imageId);

    int countByMemoryId(@Param("memoryId") long memoryId);

    List<MemoryImage> findByMemoryId(@Param("memoryId") long memoryId);

    /** 여러 추억의 대표 이미지(최소 sort_order, 동률은 최소 id)를 한 번에 조회 — 피드 썸네일용(N+1 회피). */
    List<MemoryImage> findCoverImagesByMemoryIds(@Param("memoryIds") List<Long> memoryIds);

    void deleteById(@Param("imageId") long imageId);

    void updateSortOrder(@Param("imageId") long imageId, @Param("sortOrder") int sortOrder);
}
