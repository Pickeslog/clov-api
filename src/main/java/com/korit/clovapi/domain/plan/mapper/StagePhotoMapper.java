package com.korit.clovapi.domain.plan.mapper;

import com.korit.clovapi.domain.plan.entity.PlanStagePhoto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StagePhotoMapper {

    List<PlanStagePhoto> findByPlanId(@Param("planId") long planId);

    boolean existsByPlanIdAndStage(@Param("planId") long planId, @Param("stage") String stage);

    void insert(PlanStagePhoto photo);
}
