package com.sagar.linkly.service;

import com.sagar.linkly.domain.Link;
import com.sagar.linkly.dto.BulkError;
import com.sagar.linkly.dto.BulkShortenResponse;
import com.sagar.linkly.dto.ShortenRequest;
import com.sagar.linkly.dto.ShortenResponse;
import com.sagar.linkly.encoding.Base62;
import com.sagar.linkly.repository.LinkRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShortenerService {
    private final LinkRepository repository;
    private final StringRedisTemplate redis;

    @Value("${linkly.base-url}") private String baseUrl;
    @Value("${linkly.cache-ttl-seconds}") private long cacheTtl;
    @Value("${linkly.bulk-batch-size}") private int maxBulkSize;

    @Transactional
    public ShortenResponse shorten(ShortenRequest req) {
        validate(req.longUrl());

        // 1. Custom Alias Flow (The "Ask Forgiveness" Pattern)
        if (req.customAlias() != null && !req.customAlias().isBlank()) {
            String alias = req.customAlias().trim();
            if (!alias.matches("[a-zA-Z0-9_-]{3,16}")) {
                throw new IllegalArgumentException("Invalid alias format");
            }

            Link link = Link.builder()
                    .shortCode(alias)
                    .longUrl(req.longUrl())
                    .custom(true)
                    .expiresAt(computeExpiry(req.expiresInDays()))
                    .build();

            try {
                link = repository.save(link);
                repository.flush(); // Force DB constraint check immediately
            } catch (DataIntegrityViolationException e) {
                throw new IllegalArgumentException("Alias already taken");
            }

            cachePut(alias, link.getLongUrl());
            return new ShortenResponse(alias, baseUrl + "/" + alias, link.getLongUrl());
        }

        // 2. Generated Code Flow (With Collision Retry Logic)
        Link link = null;
        int attempts = 0;
        while (link == null && attempts < 5) {
            String generatedCode = Base62.randomCode(7);
            Link tempLink = Link.builder()
                    .shortCode(generatedCode)
                    .longUrl(req.longUrl())
                    .custom(false)
                    .expiresAt(computeExpiry(req.expiresInDays()))
                    .build();
            try {
                link = repository.save(tempLink);
                repository.flush();
            } catch (DataIntegrityViolationException e) {
                attempts++;
                log.warn("Collision detected for generated code: {}, retrying...", generatedCode);
                if (attempts >= 5) {
                    throw new IllegalStateException("Failed to generate unique short code after 5 attempts");
                }
            }
        }

        cachePut(link.getShortCode(), link.getLongUrl());
        return new ShortenResponse(link.getShortCode(), baseUrl + "/" + link.getShortCode(), link.getLongUrl());
    }

    // 3. Bulk Shorten (The "All-or-Nothing" Pattern)
    public BulkShortenResponse bulkShorten(List<ShortenRequest> reqs) {
        if (reqs.size() > maxBulkSize) {
            throw new IllegalArgumentException("Max " + maxBulkSize + " URLs per request");
        }

        List<ShortenResponse> successful = new ArrayList<>();
        List<BulkError> failed = new ArrayList<>();

        for (ShortenRequest req : reqs) {
            try {
                // Try to process the individual link
                ShortenResponse response = this.shorten(req);
                successful.add(response);
            } catch (IllegalArgumentException e) {
                // If it fails (e.g., alias taken, bad URL format), catch it!
                failed.add(new BulkError(
                        req.longUrl(),
                        req.customAlias(),
                        e.getMessage() // "Alias already taken", "URL too long", etc.
                ));
            } catch (Exception e) {
                // Catch any unexpected database hiccups for this specific link
                log.error("Unexpected error shortening URL: {}", req.longUrl(), e);
                failed.add(new BulkError(req.longUrl(), req.customAlias(), "Internal processing error"));
            }
        }

        return new BulkShortenResponse(successful, failed);
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