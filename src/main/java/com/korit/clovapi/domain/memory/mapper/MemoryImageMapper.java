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

    /** 여러 추억의 대표 이미지(최소 sort_order, 동률은 최소 id) URL + 총 이미지 수를 한 번에 조회 — 피드 카드용(N+1 회피). */
    List<MemoryCover> findCoverInfoByMemoryIds(@Param("memoryIds") List<Long> memoryIds);

    void deleteById(@Param("imageId") long imageId);

    /** clov-api#98 — 소프트 삭제된 추억을 되살릴 때, 삭제 전 사진은 새 기록에 이어받지 않고 비운다. */
    void deleteByMemoryId(@Param("memoryId") long memoryId);

    void updateSortOrder(@Param("imageId") long imageId, @Param("sortOrder") int sortOrder);
}
