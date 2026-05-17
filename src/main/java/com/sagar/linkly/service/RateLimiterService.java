package com.sagar.linkly.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RateLimiterService {
    private final StringRedisTemplate redis;
    private DefaultRedisScript<List> script;

    @Value("${linkly.rate-limit.anonymous-per-minute}") private int anonRate;
    @Value("${linkly.rate-limit.bulk-max}") private int bulkMax;
    @Value("${linkly.rate-limit.bulk-minutes}") private int bulkMinutes;

    @PostConstruct
    public void init() {
        script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        script.setResultType(List.class);
    }

    // For single /shorten requests
    public boolean tryAcquire(String identityKey) {
        return executeLua("rl:" + identityKey, anonRate, anonRate / 60.0);
    }

    // For /bulk-shorten requests
    public boolean tryAcquireBulk(String identityKey) {
        double refillPerSec = bulkMax / (bulkMinutes * 60.0);
        return executeLua("rl:bulk:" + identityKey, bulkMax, refillPerSec);
    }

    private boolean executeLua(String key, int maxTokens, double refillPerSec) {
        List<?> result = redis.execute(script,
                List.of(key),
                String.valueOf(maxTokens),
                String.valueOf(refillPerSec),
                String.valueOf(Instant.now().getEpochSecond()),
                "1");
        return result != null && Long.parseLong(result.getFirst().toString()) == 1L;
    }
}