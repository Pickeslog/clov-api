package com.korit.clovapi.domain.letter.service;

import com.korit.clovapi.domain.letter.dto.LetterBroadcastResponse;
import com.korit.clovapi.domain.letter.dto.LetterFavoriteResponse;
import com.korit.clovapi.domain.letter.dto.LetterListResponse;
import com.korit.clovapi.domain.letter.dto.LetterReadResponse;
import com.korit.clovapi.domain.letter.dto.LetterResponse;
import com.korit.clovapi.domain.letter.dto.LetterSendRequest;
import com.korit.clovapi.domain.letter.entity.LuckyLetter;
import com.korit.clovapi.domain.letter.mapper.LetterMapper;
import com.korit.clovapi.domain.notification.service.NotificationService;
import com.korit.clovapi.domain.room.entity.RoomMember;
import com.korit.clovapi.domain.room.mapper.RoomMemberMapper;
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
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final LetterMapper letterMapper;
    private final RoomMemberMapper roomMemberMapper;
    private final NotificationService notificationService;

    public LetterService(LetterMapper letterMapper, RoomMemberMapper roomMemberMapper,
                          NotificationService notificationService) {
        this.letterMapper = letterMapper;
        this.roomMemberMapper = roomMemberMapper;
        this.notificationService = notificationService;
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
        // 제목은 선택 입력 — 빈 문자열은 null로 정규화해 "미입력"과 구분 없이 취급한다.
        String title = (request.title() == null || request.title().isBlank()) ? null : request.title();
        LocalDateTime sentAt = LocalDateTime.now(ZoneOffset.UTC);

        if (isBroadcast) {
            List<Long> receiverIds = roomMemberMapper.findByRoomId(roomId).stream()
                    .filter(member -> ACTIVE_STATUS.equals(member.getStatus()))
                    .map(RoomMember::getUserId)
                    .filter(memberId -> memberId != senderId)
                    .toList();
            if (!receiverIds.isEmpty()) {
                letterMapper.insertBroadcast(roomId, senderId, receiverIds, title, request.content(), emoji, sentAt);
                // #163 — receiverIds가 이미 "방 ACTIVE 멤버 · 발신자 제외"라 fanOut의 조건과
                // 똑같다. referenceId는 여러 편지가 한 번에 생기므로 LEVEL_UP과 같이 null.
                notificationService.fanOut(roomId, senderId, NotificationService.TYPE_FRIEND,
                        NotificationService.SUB_LETTER_RECEIVE, null, null);
            }
            return new LetterBroadcastResponse(receiverIds.size());
        }

        long receiverId = parseUserId(request.receiverUserId());
        requireActiveMember(roomId, receiverId);

        LuckyLetter letter = new LuckyLetter();
        letter.setRoomId(roomId);
        letter.setSenderId(senderId);
        letter.setReceiverId(receiverId);
        letter.setTitle(title);
        letter.setContent(request.content());
        letter.setEmoji(emoji);
        letter.setSentAt(sentAt);
        letterMapper.insert(letter);

        // #163 — 편지 수신 알림. 수신자가 이미 확정된 1:1이라 단일 수신자(notifyOne).
        notificationService.notifyOne(roomId, receiverId, senderId, NotificationService.TYPE_FRIEND,
                NotificationService.SUB_LETTER_RECEIVE, letter.getId(), null);

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
        if (roomMemberMapper.findActiveByRoomIdAndUserId(roomId, userId).isEmpty()) {
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
