package com.sagar.linkly.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ClickDebouncer {
    private final StringRedisTemplate redis;

    /**
     * Returns true if this is a fresh click, false if it's a duplicate within 10 seconds.
     */
    public boolean isUniqueClick(String shortCode, String ipAddress) {
        String lockKey = "lock:click:" + shortCode + ":" + ipAddress;
        // setIfAbsent returns true if the key was created, false if it already existed
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(10)));
    }
}