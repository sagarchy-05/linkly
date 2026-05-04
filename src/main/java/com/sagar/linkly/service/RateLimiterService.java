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

    @PostConstruct
    public void init() {
        script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        script.setResultType(List.class);
    }

    public boolean tryAcquire(String identityKey) {
        double refillPerSec = anonRate / 60.0;
        List<?> result = redis.execute(script,
                List.of("rl:" + identityKey),
                String.valueOf(anonRate),
                String.valueOf(refillPerSec),
                String.valueOf(Instant.now().getEpochSecond()),
                "1");
        return result != null && Long.parseLong(result.getFirst().toString()) == 1L;
    }
}