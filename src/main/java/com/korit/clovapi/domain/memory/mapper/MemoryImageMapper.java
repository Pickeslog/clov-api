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

    void deleteById(@Param("imageId") long imageId);

    void updateSortOrder(@Param("imageId") long imageId, @Param("sortOrder") int sortOrder);
}
