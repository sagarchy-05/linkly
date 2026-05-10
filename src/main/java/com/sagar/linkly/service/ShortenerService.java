package com.sagar.linkly.service;

import com.sagar.linkly.domain.Link;
import com.sagar.linkly.dto.ShortenRequest;
import com.sagar.linkly.dto.ShortenResponse;
import com.sagar.linkly.encoding.Base62;
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
public class ShortenerService {
    private final LinkRepository repository;
    private final StringRedisTemplate redis;

    @Value("${linkly.base-url}") private String baseUrl;
    @Value("${linkly.cache-ttl-seconds}") private long cacheTtl;

    @Transactional
    public ShortenResponse shorten(ShortenRequest req) {
        validate(req.longUrl());

        // Custom Alias Flow
        if (req.customAlias() != null && !req.customAlias().isBlank()) {
            String alias = req.customAlias().trim();
            if (!alias.matches("[a-zA-Z0-9_-]{3,16}"))
                throw new IllegalArgumentException("Invalid alias format");
            if (repository.existsByShortCode(alias))
                throw new IllegalArgumentException("Alias already taken");

            Link link = Link.builder()
                    .shortCode(alias)
                    .longUrl(req.longUrl())
                    .custom(true)
                    .expiresAt(computeExpiry(req.expiresInDays()))
                    .build();
            link = repository.save(link);
            cachePut(alias, link.getLongUrl());
            return new ShortenResponse(alias, baseUrl + "/" + alias, link.getLongUrl());
        }

        String generatedCode;
        do {
            // Generates a random 7-character string (e.g., "a3f8b9e")
            generatedCode = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        } while (repository.existsByShortCode(generatedCode));

        Link link = Link.builder()
                .shortCode(generatedCode)
                .longUrl(req.longUrl())
                .custom(false)
                .expiresAt(computeExpiry(req.expiresInDays()))
                .build();

        link = repository.save(link);
        cachePut(generatedCode, link.getLongUrl());

        return new ShortenResponse(generatedCode, baseUrl + "/" + generatedCode, link.getLongUrl());
    }

    private void validate(String url) {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("URL required");
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            throw new IllegalArgumentException("URL must use http or https");
        if (url.length() > 2048) throw new IllegalArgumentException("URL too long");
    }

    private OffsetDateTime computeExpiry(Long expiresInDays) {
        if (expiresInDays == null || expiresInDays <= 0) {
            return null;
        }
        return OffsetDateTime.now().plusDays(expiresInDays);
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