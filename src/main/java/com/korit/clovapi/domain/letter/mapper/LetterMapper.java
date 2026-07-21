package com.korit.clovapi.domain.letter.mapper;

import com.korit.clovapi.domain.letter.dto.LetterDetailRow;
import com.korit.clovapi.domain.letter.entity.LuckyLetter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface LetterMapper {

    boolean isActiveRoomMember(@Param("roomId") long roomId, @Param("userId") long userId);

    List<Long> findActiveMemberUserIds(@Param("roomId") long roomId, @Param("excludeUserId") long excludeUserId);

    void insert(LuckyLetter letter);

    void insertBroadcast(
            @Param("roomId") long roomId,
            @Param("senderId") long senderId,
            @Param("receiverIds") List<Long> receiverIds,
            @Param("content") String content,
            @Param("emoji") String emoji,
            @Param("sentAt") LocalDateTime sentAt
    );

    Optional<LuckyLetter> findById(@Param("id") long id);

    List<LetterDetailRow> findBox(
            @Param("roomId") long roomId,
            @Param("viewerId") long viewerId,
            @Param("box") String box
    );

    Optional<LetterDetailRow> findDetailById(@Param("id") long id, @Param("viewerId") long viewerId);

    int markRead(@Param("id") long id, @Param("readAt") LocalDateTime readAt);

    boolean existsFavorite(@Param("letterId") long letterId, @Param("userId") long userId);

    void insertFavorite(@Param("letterId") long letterId, @Param("userId") long userId);

    void deleteFavorite(@Param("letterId") long letterId, @Param("userId") long userId);
}
