package com.korit.clovapi.domain.plan.mapper;
import com.korit.clovapi.domain.plan.entity.PlanStagePhoto; import org.apache.ibatis.annotations.*; import java.util.*;
@Mapper public interface StagePhotoMapper { List<PlanStagePhoto> findByPlanId(@Param("planId") long id); boolean existsByPlanIdAndStage(@Param("planId") long id,@Param("stage") String s); void insert(PlanStagePhoto p); }
