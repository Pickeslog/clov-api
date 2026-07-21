package com.korit.clovapi.domain.plan.mapper;

import com.korit.clovapi.domain.plan.dto.PlanRequests;
import com.korit.clovapi.domain.plan.entity.Plan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PlanMapper {

    void insert(Plan plan);

    Optional<Plan> findById(@Param("planId") long planId);

    List<Plan> findByRoomId(
            @Param("roomId") long roomId,
            @Param("status") String status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    int updateByIdAndWriterId(
            @Param("planId") long planId,
            @Param("writerId") long writerId,
            @Param("request") PlanRequests.Update request
    );

    int deleteByIdAndWriterId(@Param("planId") long planId, @Param("writerId") long writerId);

    int complete(@Param("planId") long planId, @Param("completedAt") LocalDateTime completedAt);

    int cancelByIdAndWriterId(@Param("planId") long planId, @Param("writerId") long writerId);

    int skipMemory(@Param("planId") long planId);
}
