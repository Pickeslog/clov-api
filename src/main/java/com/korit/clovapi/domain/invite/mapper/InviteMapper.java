package com.korit.clovapi.domain.invite.mapper;

import com.korit.clovapi.domain.invite.entity.RoomInvite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface InviteMapper {

    /**
     * A안(방당 고정 회전 코드): 방마다 초대 코드는 한 행. 재발급은 새 행이 아니라 제자리 회전이다.
     * room_id UNIQUE 제약 위에서 INSERT ... ON DUPLICATE KEY UPDATE로 첫 생성·회전을 원자적으로 처리.
     */
    void upsertByRoomId(@Param("roomId") long roomId, @Param("inviteCode") String inviteCode,
                        @Param("createdBy") long createdBy, @Param("expiresAt") LocalDateTime expiresAt);

    boolean existsByInviteCode(@Param("inviteCode") String inviteCode);

    Optional<RoomInvite> findById(@Param("inviteId") long inviteId);

    Optional<RoomInvite> findByInviteCode(@Param("inviteCode") String inviteCode);

    /** 방의 활성 초대 코드(0 또는 1행). */
    List<RoomInvite> findActiveByRoomId(@Param("roomId") long roomId);

    int cancelByIdAndCreatorId(@Param("inviteId") long inviteId, @Param("createdBy") long createdBy);

    void insertJoinNotifications(@Param("roomId") long roomId, @Param("actorId") long actorId,
                                 @Param("referenceId") long referenceId);
}
