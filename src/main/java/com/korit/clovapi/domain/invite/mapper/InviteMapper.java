package com.korit.clovapi.domain.invite.mapper;

import com.korit.clovapi.domain.invite.entity.RoomInvite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface InviteMapper {

    void insert(RoomInvite invite);

    boolean existsByInviteCode(@Param("inviteCode") String inviteCode);

    Optional<RoomInvite> findById(@Param("inviteId") long inviteId);

    Optional<RoomInvite> findByInviteCode(@Param("inviteCode") String inviteCode);

    List<RoomInvite> findByRoomId(@Param("roomId") long roomId);

    int cancelByIdAndCreatorId(@Param("inviteId") long inviteId, @Param("createdBy") long createdBy);

    int markUsedIfActive(@Param("inviteId") long inviteId, @Param("usedAt") LocalDateTime usedAt);

    void insertJoinNotifications(@Param("roomId") long roomId, @Param("actorId") long actorId,
                                 @Param("referenceId") long referenceId);
}
