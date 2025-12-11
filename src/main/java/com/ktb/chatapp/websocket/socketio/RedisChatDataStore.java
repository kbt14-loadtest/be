package com.ktb.chatapp.websocket.socketio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed implementation of ChatDataStore using Redisson client.
 * Supports multi-instance deployment with shared state across Socket.IO servers.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisChatDataStore implements ChatDataStore {

    private static final String KEY_PREFIX = "socketio:chat:";
    private static final long DEFAULT_TTL_SECONDS = 3600; // 1 hour default TTL

    private final RedissonClient redissonClient;

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            RBucket<Object> bucket = redissonClient.getBucket(KEY_PREFIX + key);
            Object value = bucket.get();

            if (value == null) {
                return Optional.empty();
            }

            return Optional.of(type.cast(value));
        } catch (ClassCastException e) {
            log.warn("Type mismatch for key {}: expected {}, got {}",
                    key, type.getName(), e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error retrieving value for key {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void set(String key, Object value) {
        try {
            RBucket<Object> bucket = redissonClient.getBucket(KEY_PREFIX + key);
            bucket.set(value, DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Error storing value for key {}: {}", key, e.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        try {
            RBucket<Object> bucket = redissonClient.getBucket(KEY_PREFIX + key);
            bucket.delete();
        } catch (Exception e) {
            log.error("Error deleting key {}: {}", key, e.getMessage());
        }
    }

    @Override
    public int size() {
        try {
            // Note: This is an approximation as Redis doesn't efficiently support this
            return (int) redissonClient.getKeys().countExists(KEY_PREFIX + "*");
        } catch (Exception e) {
            log.error("Error getting size: {}", e.getMessage());
            return 0;
        }
    }
}
