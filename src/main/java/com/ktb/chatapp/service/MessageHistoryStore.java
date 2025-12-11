package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.MessageResponse;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageHistoryStore {

    private static final int MAX_MESSAGES = 99999;
    private static final Duration TTL = Duration.ZERO;

    private final RedisTemplate<String, MessageResponse> messageResponseRedisTemplate;

    private String buildKey(String roomId) {
        return "room:" + roomId + ":messages";
    }


    public void append(String roomId, MessageResponse message) {
        String key = buildKey(roomId);

        messageResponseRedisTemplate.opsForList().rightPush(key, message);
        messageResponseRedisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);

        if (!TTL.isZero() && !TTL.isNegative()) {
            messageResponseRedisTemplate.expire(key, TTL);
        }

        log.info("[HISTORY] append - roomId={}, size={}, maxMessages={}, ttl={}",
                roomId,
                messageResponseRedisTemplate.opsForList().size(key),
                MAX_MESSAGES,
                TTL);
    }

    public List<MessageResponse> getAll(String roomId) {
        String key = buildKey(roomId);
        List<MessageResponse> list =
                messageResponseRedisTemplate.opsForList().range(key, 0, -1);
        return list != null ? list : List.of();
    }

    public long getSize(String roomId) {
        String key = buildKey(roomId);
        Long size = messageResponseRedisTemplate.opsForList().size(key);
        return size != null ? size : 0L;
    }
}
