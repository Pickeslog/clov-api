package com.korit.clovapi.domain.letter.service;

import com.korit.clovapi.domain.letter.dto.LetterBroadcastResponse;
import com.korit.clovapi.domain.letter.dto.LetterFavoriteResponse;
import com.korit.clovapi.domain.letter.dto.LetterListResponse;
import com.korit.clovapi.domain.letter.dto.LetterReadResponse;
import com.korit.clovapi.domain.letter.dto.LetterResponse;
import com.korit.clovapi.domain.letter.dto.LetterSendRequest;
import com.korit.clovapi.domain.letter.entity.LuckyLetter;
import com.korit.clovapi.domain.letter.mapper.LetterMapper;
import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class LetterService {

    private static final String DEFAULT_EMOJI = "💌";

    private final LetterMapper letterMapper;

    public LetterService(LetterMapper letterMapper) {
        this.letterMapper = letterMapper;
    }

    @Transactional
    public Object send(long roomId, long senderId, LetterSendRequest request) {
        requireActiveMember(roomId, senderId);

        boolean hasReceiver = request.receiverUserId() != null && !request.receiverUserId().isBlank();
        boolean isBroadcast = Boolean.TRUE.equals(request.broadcast());
        if (hasReceiver == isBroadcast) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }

        String emoji = (request.emoji() == null || request.emoji().isBlank()) ? DEFAULT_EMOJI : request.emoji();
        LocalDateTime sentAt = LocalDateTime.now(ZoneOffset.UTC);

        if (isBroadcast) {
            List<Long> receiverIds = letterMapper.findActiveMemberUserIds(roomId, senderId);
            if (!receiverIds.isEmpty()) {
                letterMapper.insertBroadcast(roomId, senderId, receiverIds, request.content(), emoji, sentAt);
            }
            return new LetterBroadcastResponse(receiverIds.size());
        }

        long receiverId = parseUserId(request.receiverUserId());
        requireActiveMember(roomId, receiverId);

        LuckyLetter letter = new LuckyLetter();
        letter.setRoomId(roomId);
        letter.setSenderId(senderId);
        letter.setReceiverId(receiverId);
        letter.setContent(request.content());
        letter.setEmoji(emoji);
        letter.setSentAt(sentAt);
        letterMapper.insert(letter);

        return LetterResponse.from(letterMapper.findDetailById(letter.getId(), senderId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND)));
    }

    public LetterListResponse getBox(long roomId, long viewerId, String box) {
        requireActiveMember(roomId, viewerId);
        if (!"received".equals(box) && !"sent".equals(box)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }

        List<LetterResponse> items = letterMapper.findBox(roomId, viewerId, box).stream()
                .map(LetterResponse::from)
                .toList();
        return LetterListResponse.of(items);
    }

    @Transactional
    public LetterReadResponse markRead(long letterId, long userId) {
        LuckyLetter letter = findLetterOrThrow(letterId);
        if (letter.getReceiverId() != userId) {
            throw new DomainException(ErrorCode.FORBIDDEN);
        }

        LocalDateTime readAt = letter.getReadAt();
        if (readAt == null) {
            readAt = LocalDateTime.now(ZoneOffset.UTC);
            letterMapper.markRead(letterId, readAt);
        }
        return new LetterReadResponse(readAt);
    }

    @Transactional
    public LetterFavoriteResponse toggleFavorite(long letterId, long userId) {
        LuckyLetter letter = findLetterOrThrow(letterId);
        if (letter.getSenderId() != userId && letter.getReceiverId() != userId) {
            throw new DomainException(ErrorCode.FORBIDDEN);
        }

        boolean currentlyFavorite = letterMapper.existsFavorite(letterId, userId);
        if (currentlyFavorite) {
            letterMapper.deleteFavorite(letterId, userId);
        } else {
            letterMapper.insertFavorite(letterId, userId);
        }
        return new LetterFavoriteResponse(!currentlyFavorite);
    }

    private LuckyLetter findLetterOrThrow(long letterId) {
        return letterMapper.findById(letterId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private void requireActiveMember(long roomId, long userId) {
        if (!letterMapper.isActiveRoomMember(roomId, userId)) {
            throw new DomainException(ErrorCode.ROOM_MEMBER_NOT_FOUND);
        }
    }

    private long parseUserId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
