package com.bank.account.infrastructure.messaging;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class KafkaIdempotencyStore {

    private final StringRedisTemplate redisTemplate;
    private static final Duration PENDING_TTL = Duration.ofMinutes(5);
    private static final Duration COMPLETED_TTL = Duration.ofDays(7);

    public KafkaIdempotencyStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * @return true if the message is new and lock was acquired, false if it's already being processed or completed
     */
    public boolean checkAndAcquire(String eventId, String consumerGroup) {
        String key = "idemp:" + consumerGroup + ":" + eventId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "PENDING", PENDING_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    public void markCompleted(String eventId, String consumerGroup) {
        String key = "idemp:" + consumerGroup + ":" + eventId;
        redisTemplate.opsForValue().set(key, "COMPLETED", COMPLETED_TTL);
    }

    public void release(String eventId, String consumerGroup) {
        String key = "idemp:" + consumerGroup + ":" + eventId;
        // Only release if it's still PENDING. If it's COMPLETED, we shouldn't delete it.
        String status = redisTemplate.opsForValue().get(key);
        if ("PENDING".equals(status)) {
            redisTemplate.delete(key);
        }
    }
}
