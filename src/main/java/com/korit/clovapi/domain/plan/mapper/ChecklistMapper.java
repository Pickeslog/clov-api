package com.korit.clovapi.domain.plan.mapper;

import com.korit.clovapi.domain.plan.dto.PlanRequests;
import com.korit.clovapi.domain.plan.entity.PlanChecklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ChecklistMapper {

    void insert(PlanChecklist checklist);

    List<PlanChecklist> findByPlanId(@Param("planId") long planId);

    Optional<PlanChecklist> findById(@Param("checklistId") long checklistId);

    int update(@Param("checklistId") long checklistId, @Param("request") PlanRequests.ChecklistUpdate request);

    int deleteById(@Param("checklistId") long checklistId);

    void deleteByPlanId(@Param("planId") long planId);
}
