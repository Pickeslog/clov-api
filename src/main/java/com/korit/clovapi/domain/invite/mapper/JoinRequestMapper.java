package com.korit.clovapi.domain.invite.mapper;

import com.korit.clovapi.domain.invite.entity.RoomJoinRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface JoinRequestMapper {

    void insert(RoomJoinRequest request);

    Optional<RoomJoinRequest> findById(@Param("joinRequestId") long joinRequestId);

    List<RoomJoinRequest> findPendingByRoomId(@Param("roomId") long roomId);

    int acceptWithVersion(@Param("joinRequestId") long joinRequestId, @Param("version") int version,
                          @Param("acceptedBy") long acceptedBy, @Param("acceptedAt") LocalDateTime acceptedAt,
                          @Param("undoDeadlineAt") LocalDateTime undoDeadlineAt);

    int rejectWithVersion(@Param("joinRequestId") long joinRequestId, @Param("version") int version,
                          @Param("rejectedAt") LocalDateTime rejectedAt);

    int undoWithVersion(@Param("joinRequestId") long joinRequestId, @Param("version") int version,
                        @Param("acceptedBy") long acceptedBy);
}
