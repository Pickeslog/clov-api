package com.korit.clovapi.domain.room.mapper;

import com.korit.clovapi.domain.room.dto.UpdateRoomRequest;
import com.korit.clovapi.domain.room.entity.FriendshipExpLog;
import com.korit.clovapi.domain.room.entity.Room;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface RoomMapper {

    void insert(Room room);

    Optional<Room> findDetailByIdAndUserId(@Param("roomId") long roomId, @Param("userId") long userId);

    List<Room> findSummariesByMemberUserId(@Param("userId") long userId);

    Optional<Room> findById(@Param("roomId") long roomId);

    void update(@Param("roomId") long roomId, @Param("request") UpdateRoomRequest request);

    void insertRoomUpdateNotifications(@Param("roomId") long roomId, @Param("actorId") long actorId);

    void updateStatusInactive(@Param("roomId") long roomId, @Param("scheduledDeleteAt") LocalDateTime scheduledDeleteAt);

    void revive(@Param("roomId") long roomId);

    List<FriendshipExpLog> findExpLogs(@Param("roomId") long roomId);

    int countMascotInteractionsToday(@Param("roomId") long roomId, @Param("userId") long userId,
                                     @Param("startOfDay") LocalDateTime startOfDay);

    void insertExpLog(FriendshipExpLog log);

    Optional<Room> findByIdForUpdate(@Param("roomId") long roomId);

    void updateLevelAndExp(@Param("roomId") long roomId, @Param("friendshipLevel") int friendshipLevel,
                           @Param("expPoint") int expPoint);

    int sumExpDeltaByReference(@Param("roomId") long roomId, @Param("actionType") String actionType,
                               @Param("referenceId") long referenceId);
}
