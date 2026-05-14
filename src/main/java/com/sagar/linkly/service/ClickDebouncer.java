package com.sagar.linkly.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClickDebouncer {
    private final StringRedisTemplate redis;

    /**
     * Returns true if this click should be counted, false if it's a duplicate
     * within the debounce window (10s per shortCode+IP).
     *
     * Fails open: if Redis is unavailable, returns true so the click is still
     * published. The redirect path must never fail because of analytics infra.
     */
    public boolean isUniqueClick(String shortCode, String ipAddress) {
        String lockKey = "lock:click:" + shortCode + ":" + ipAddress;
        try {
            return Boolean.TRUE.equals(
                    redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(10))
            );
        } catch (Exception e) {
            log.warn("Debounce check failed for {}, allowing publish: {}", shortCode, e.getMessage());
            return true;
        }
    }
}
