package com.korit.clovapi.domain.plan.service;

import com.korit.clovapi.domain.plan.dto.PlanRequests;
import com.korit.clovapi.domain.plan.dto.PlanResponses;
import com.korit.clovapi.domain.plan.entity.Plan;
import com.korit.clovapi.domain.plan.entity.PlanChecklist;
import com.korit.clovapi.domain.plan.entity.PlanStagePhoto;
import com.korit.clovapi.domain.plan.mapper.ChecklistMapper;
import com.korit.clovapi.domain.plan.mapper.PlanMapper;
import com.korit.clovapi.domain.plan.mapper.StagePhotoMapper;
import com.korit.clovapi.domain.room.mapper.RoomMemberMapper;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import com.korit.clovapi.global.storage.StoragePresigner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PlanService {

    private static final List<String> STAGES = List.of("PROPOSAL", "SCHEDULING", "CONFIRMED", "MEETING");

    private final PlanMapper planMapper;
    private final ChecklistMapper checklistMapper;
    private final StagePhotoMapper stagePhotoMapper;
    private final RoomMemberMapper roomMemberMapper;
    private final StoragePresigner storagePresigner;

    public PlanService(PlanMapper planMapper, ChecklistMapper checklistMapper,
                       StagePhotoMapper stagePhotoMapper, RoomMemberMapper roomMemberMapper,
                       StoragePresigner storagePresigner) {
        this.planMapper = planMapper;
        this.checklistMapper = checklistMapper;
        this.stagePhotoMapper = stagePhotoMapper;
        this.roomMemberMapper = roomMemberMapper;
        this.storagePresigner = storagePresigner;
    }

    @Transactional
    public PlanResponses.Detail create(long roomId, long userId, PlanRequests.Create request) {
        assertActiveMember(roomId, userId);
        Plan plan = new Plan();
        plan.setRoomId(roomId);
        plan.setWriterId(userId);
        plan.setTitle(request.title());
        plan.setPlanDate(request.planDate());
        plan.setDescription(request.description());
        planMapper.insert(plan);
        return detail(plan.getId(), userId);
    }

    public PlanResponses.Items<PlanResponses.Summary> list(long roomId, long userId, String status,
                                                              LocalDate from, LocalDate to) {
        assertActiveMember(roomId, userId);
        return new PlanResponses.Items<>(planMapper.findByRoomId(roomId, status, from, to).stream()
                .map(PlanResponses.Summary::from)
                .toList());
    }

    public PlanResponses.Detail detail(long planId, long userId) {
        Plan plan = findPlan(planId);
        assertActiveMember(plan.getRoomId(), userId);
        return PlanResponses.Detail.from(plan, checklistMapper.findByPlanId(planId));
    }

    @Transactional
    public PlanResponses.Detail update(long planId, long userId, PlanRequests.Update request) {
        Plan plan = findPlan(planId);
        assertActiveMember(plan.getRoomId(), userId);
        if (planMapper.updateByIdAndWriterId(planId, userId, request) != 1) {
            throw new DomainException(ErrorCode.NOT_WRITER);
        }
        return detail(planId, userId);
    }

    @Transactional
    public void delete(long planId, long userId) {
        Plan plan = findPlan(planId);
        assertActiveMember(plan.getRoomId(), userId);
        if (planMapper.deleteByIdAndWriterId(planId, userId) != 1) {
            throw new DomainException(ErrorCode.NOT_WRITER);
        }
    }

    @Transactional
    public PlanResponses.Detail complete(long planId, long userId) {
        Plan plan = findPlan(planId);
        assertActiveMember(plan.getRoomId(), userId);
        planMapper.complete(planId, LocalDateTime.now(ZoneOffset.UTC));
        return detail(planId, userId);
    }

    @Transactional
    public PlanResponses.Detail cancel(long planId, long userId) {
        Plan plan = findPlan(planId);
        assertActiveMember(plan.getRoomId(), userId);
        if (planMapper.cancelByIdAndWriterId(planId, userId) != 1) {
            throw new DomainException(ErrorCode.NOT_WRITER);
        }
        return detail(planId, userId);
    }

    @Transactional
    public PlanResponses.Detail skip(long planId, long userId) {
        Plan plan = findPlan(planId);
        assertActiveMember(plan.getRoomId(), userId);
        planMapper.skipMemory(planId);
        return detail(planId, userId);
    }

    @Transactional
    public PlanResponses.Checklist addChecklist(long planId, long userId, PlanRequests.Checklist request) {
        Plan plan = findPlan(planId);
        assertActiveMember(plan.getRoomId(), userId);
        PlanChecklist checklist = new PlanChecklist();
        checklist.setPlanId(planId);
        checklist.setContent(request.content());
        checklistMapper.insert(checklist);
        return PlanResponses.Checklist.from(checklistMapper.findById(checklist.getId())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND)));
    }

    @Transactional
    public PlanResponses.Checklist updateChecklist(long checklistId, long userId,
                                                    PlanRequests.ChecklistUpdate request) {
        PlanChecklist checklist = checklistMapper.findById(checklistId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        assertActiveMember(findPlan(checklist.getPlanId()).getRoomId(), userId);
        checklistMapper.update(checklistId, request);
        return PlanResponses.Checklist.from(checklistMapper.findById(checklistId).orElseThrow());
    }

    @Transactional
    public void deleteChecklist(long checklistId, long userId) {
        PlanChecklist checklist = checklistMapper.findById(checklistId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        assertActiveMember(findPlan(checklist.getPlanId()).getRoomId(), userId);
        checklistMapper.deleteById(checklistId);
    }

    public PlanResponses.Items<PlanResponses.Stage> stages(long planId, long userId) {
        Plan plan = findPlan(planId);
        assertActiveMember(plan.getRoomId(), userId);
        return new PlanResponses.Items<>(stageItems(planId));
    }

    public PlanResponses.Presign presign(long planId, long userId, PlanRequests.Presign request) {
        Plan plan = findPlan(planId);
        assertActiveMember(plan.getRoomId(), userId);
        assertStageAvailable(planId, request.stage());
        String objectKey = "rooms/%d/plans/%d/%s-%s%s".formatted(
                plan.getRoomId(), planId,
                request.stage().toLowerCase(Locale.ROOT), UUID.randomUUID(),
                StoragePresigner.extensionFor(request.contentType()));
        StoragePresigner.PresignResult result = storagePresigner.presignPut(objectKey, request.contentType());
        return new PlanResponses.Presign(result.uploadUrl(), result.imageUrl(), result.expiresIn());
    }

    @Transactional
    public PlanResponses.Stage commit(long planId, long userId, PlanRequests.Stage request) {
        Plan plan = findPlan(planId);
        assertActiveMember(plan.getRoomId(), userId);
        assertStageAvailable(planId, request.stage());
        if (stagePhotoMapper.existsByPlanIdAndStage(planId, request.stage())) {
            throw new DomainException(ErrorCode.STAGE_ALREADY_UPLOADED);
        }
        PlanStagePhoto photo = new PlanStagePhoto();
        photo.setPlanId(planId);
        photo.setStage(request.stage());
        photo.setImageUrl(request.imageUrl());
        photo.setUploadedBy(userId);
        stagePhotoMapper.insert(photo);
        return stageItems(planId).stream()
                .filter(stage -> stage.stage().equals(request.stage()))
                .findFirst()
                .orElseThrow();
    }

    private List<PlanResponses.Stage> stageItems(long planId) {
        Map<String, PlanStagePhoto> photosByStage = new HashMap<>();
        for (PlanStagePhoto photo : stagePhotoMapper.findByPlanId(planId)) {
            photosByStage.put(photo.getStage(), photo);
        }
        List<PlanResponses.Stage> items = new ArrayList<>();
        boolean previousDone = true;
        for (String stage : STAGES) {
            PlanStagePhoto photo = photosByStage.get(stage);
            String state = photo != null ? "DONE" : previousDone ? "ACTIVE" : "LOCKED";
            PlanResponses.Writer uploadedBy = photo == null ? null
                    : new PlanResponses.Writer(String.valueOf(photo.getUploadedBy()), photo.getNickname(), photo.getProfileImageUrl());
            items.add(new PlanResponses.Stage(stage, state, photo == null ? null : photo.getImageUrl(),
                    uploadedBy, photo == null ? null : photo.getCreatedAt()));
            previousDone = photo != null;
        }
        return items;
    }

    private void assertStageAvailable(long planId, String stage) {
        int index = STAGES.indexOf(stage);
        if (index < 0 || (index > 0 && !stagePhotoMapper.existsByPlanIdAndStage(planId, STAGES.get(index - 1)))) {
            throw new DomainException(ErrorCode.STAGE_LOCKED);
        }
    }

    private Plan findPlan(long planId) {
        return planMapper.findById(planId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private void assertActiveMember(long roomId, long userId) {
        if (roomMemberMapper.findActiveByRoomIdAndUserId(roomId, userId).isEmpty()) {
            throw new DomainException(ErrorCode.ROOM_MEMBER_NOT_FOUND);
        }
    }
}
