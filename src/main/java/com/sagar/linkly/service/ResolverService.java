package com.sagar.linkly.service;

import com.sagar.linkly.domain.Link;
import com.sagar.linkly.exception.LinkExpiredException;
import com.sagar.linkly.exception.LinkNotFoundException;
import com.sagar.linkly.repository.LinkRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResolverService {
    private final LinkRepository repository;
    private final StringRedisTemplate redis;

    @Value("${linkly.cache-ttl-seconds}") private long cacheTtl;

    @Transactional(readOnly = true)
    public String resolve(String shortCode) {
        // Try cache first
        String cached = redis.opsForValue().get(cacheKey(shortCode));
        if (cached != null) {
            return cached;
        }

        // Fallback to DB
        Link link = repository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new LinkNotFoundException(shortCode));

        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new LinkExpiredException(shortCode);
        }

        cachePut(shortCode, link.getLongUrl());
        return link.getLongUrl();
    }

    private String cacheKey(String code) { return "link:" + code; }

    private void cachePut(String code, String longUrl) {
        try {
            redis.opsForValue().set(cacheKey(code), longUrl, Duration.ofSeconds(cacheTtl));
        } catch (Exception e) {
            log.warn("Cache write failed for code={}: {}", code, e.getMessage());
        }
    }
}