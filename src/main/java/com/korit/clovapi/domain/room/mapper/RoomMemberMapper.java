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

    // 방 목록(GET /rooms) memberAvatars용 배치 조회 — 계약 §4-3·§6(clov-api#141). 방마다
    // 따로 부르면(N+1) 문제가 클라이언트에서 서버로 옮겨갈 뿐이라, roomIds 전체를 한 번에 IN 절로 묶는다.
    List<RoomMember> findActiveByRoomIds(@Param("roomIds") List<Long> roomIds);

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
