package com.korit.clovapi.domain.plan.mapper;
import com.korit.clovapi.domain.plan.entity.PlanChecklist; import com.korit.clovapi.domain.plan.dto.PlanRequests; import org.apache.ibatis.annotations.*; import java.util.*;
@Mapper public interface ChecklistMapper { void insert(PlanChecklist c); List<PlanChecklist> findByPlanId(@Param("planId") long id); Optional<PlanChecklist> findById(@Param("checklistId") long id); int update(@Param("checklistId") long id,@Param("request") PlanRequests.ChecklistUpdate r); int deleteById(@Param("checklistId") long id); }
