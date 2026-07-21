package com.korit.clovapi.domain.room.mapper;

import com.korit.clovapi.domain.room.entity.RoomMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface RoomMemberMapper {

    void insert(RoomMember member);

    Optional<RoomMember> findActiveByRoomIdAndUserId(@Param("roomId") long roomId, @Param("userId") long userId);

    List<RoomMember> findByRoomId(@Param("roomId") long roomId);

    void leave(@Param("roomId") long roomId, @Param("userId") long userId, @Param("leftAt") LocalDateTime leftAt);

    int countActiveByRoomId(@Param("roomId") long roomId);

    int countActiveForUpdateByRoomId(@Param("roomId") long roomId);

    int deleteByRoomIdAndUserId(@Param("roomId") long roomId, @Param("userId") long userId);

    void updateStatusMessage(@Param("roomId") long roomId, @Param("userId") long userId,
                             @Param("statusMessage") String statusMessage);

    void updateFavorite(@Param("roomId") long roomId, @Param("userId") long userId,
                        @Param("isFavorite") boolean isFavorite);

    int revive(@Param("roomId") long roomId, @Param("userId") long userId, @Param("joinedAt") LocalDateTime joinedAt);
}
