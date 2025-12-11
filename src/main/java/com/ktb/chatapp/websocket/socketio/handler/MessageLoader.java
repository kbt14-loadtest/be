package com.ktb.chatapp.websocket.socketio.handler;

import com.ktb.chatapp.dto.FetchMessagesRequest;
import com.ktb.chatapp.dto.FetchMessagesResponse;
import com.ktb.chatapp.dto.MessageResponse;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.MessageHistoryStore;
import com.ktb.chatapp.service.MessageReadStatusService;
import com.ktb.chatapp.util.image.ImageUtils;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import static java.util.Collections.emptyList;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageLoader {

    private static final int BATCH_SIZE = 30;
    private static final int MAX_LIMIT = 100;

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageResponseMapper messageResponseMapper;
    private final MessageReadStatusService messageReadStatusService;
    private final MessageHistoryStore messageHistoryStore;
    private final ImageUtils imageUtils;

    public FetchMessagesResponse loadMessages(FetchMessagesRequest data, String userId) {
        String roomId = data.roomId();
        int limit = Math.min(data.limit(BATCH_SIZE), MAX_LIMIT);
        LocalDateTime before = data.before(LocalDateTime.now());

        try {
            FetchMessagesResponse fromRedis = loadFromRedis(roomId, before, limit, userId);
            if (fromRedis != null) {
                log.debug("Messages loaded from Redis store - roomId: {}, before: {}, limit: {}, count: {}, hasMore: {}",
                        roomId, before, limit, fromRedis.getMessages().size(), fromRedis.isHasMore());
                return fromRedis;
            }

            FetchMessagesResponse fromDb = loadFromDb(roomId, before, limit, userId);
            log.debug("Messages loaded from DB - roomId: {}, before: {}, limit: {}, count: {}, hasMore: {}",
                    roomId, before, limit, fromDb.getMessages().size(), fromDb.isHasMore());
            return fromDb;
        } catch (Exception e) {
            log.error("Error loading messages for room {}", roomId, e);
            return FetchMessagesResponse.builder()
                    .messages(emptyList())
                    .hasMore(false)
                    .build();
        }
    }

    @Nullable
    private FetchMessagesResponse loadFromRedis(
            String roomId,
            LocalDateTime before,
            int limit,
            String userId
    ) {
        List<MessageResponse> history = messageHistoryStore.getAll(roomId);
        if (history.isEmpty()) {
            return null;
        }

        long beforeMillis = before.toInstant(ZoneOffset.UTC).toEpochMilli();

        List<MessageResponse> filtered = history.stream()
                .filter(m -> m.getTimestamp() < beforeMillis)
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            return null;
        }

        int total = filtered.size();
        int fromIndex = Math.max(total - limit, 0);
        List<MessageResponse> page = filtered.subList(fromIndex, total);

        var messageIds = page.stream().map(MessageResponse::getId).toList();
        messageReadStatusService.updateReadStatus(messageIds, userId);

        boolean hasMore = fromIndex > 0;

        return FetchMessagesResponse.builder()
                .messages(page)
                .hasMore(hasMore)
                .build();
    }

    private FetchMessagesResponse loadFromDb(
            String roomId,
            LocalDateTime before,
            int limit,
            String userId
    ) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by("timestamp").descending());

        Page<Message> messagePage = messageRepository
                .findByRoomIdAndIsDeletedAndTimestampBefore(roomId, false, before, pageable);

        List<Message> messages = messagePage.getContent();

        List<Message> sortedMessages = messages.reversed();

        var messageIds = sortedMessages.stream().map(Message::getId).toList();
        messageReadStatusService.updateReadStatus(messageIds, userId);

        List<MessageResponse> messageResponses = sortedMessages.stream()
                .map(message -> {
                    var user = findUserById(message.getSenderId());
                    String presignedProfileUrl="";
                    if (user != null && user.getProfileImageKey() != null) {
                        presignedProfileUrl = imageUtils.generatePresignedUrlWithKey(user.getProfileImageKey(), Duration.ofHours(1));
                    }
                    return messageResponseMapper.mapToMessageResponse(message, user, presignedProfileUrl);
                })
                .collect(Collectors.toList());

        boolean hasMore = messagePage.hasNext();

        return FetchMessagesResponse.builder()
                .messages(messageResponses)
                .hasMore(hasMore)
                .build();
    }

    /**
     * AI 메시지 등 senderId 없을 수 있음
     */
    @Nullable
    private User findUserById(String id) {
        if (id == null) {
            return null;
        }
        return userRepository.findById(id).orElse(null);
    }
}
