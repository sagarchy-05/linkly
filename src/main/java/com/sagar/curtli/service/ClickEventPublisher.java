package com.sagar.curtli.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClickEventPublisher {
    private final StringRedisTemplate redis;
    @Value("${curtli.click-stream.name}") private String streamName;
    @Value("${curtli.ip-hash-secret:change-me}") private String secret;

    @Async("clickPublisherExecutor")
    public void publish(String shortCode, String ipAddress, String referrer, String userAgent) {
        try {
            Map<String, String> body = Map.of(
                    "shortCode", shortCode,
                    "ipHash", hashIp(ipAddress),
                    "referrer", safe(referrer),
                    "userAgent", safe(userAgent),
                    "ts", Instant.now().toString()
            );
            MapRecord<String, String, String> record = StreamRecords.mapBacked(body)
                    .withStreamKey(streamName);
            redis.opsForStream().add(record);
        } catch (Exception e) {
            log.warn("Failed to publish click event: {}", e.getMessage());
        }
    }

    private String hashIp(String ip) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(secret.getBytes());
            return HexFormat.of().formatHex(md.digest(ip.getBytes())).substring(0, 16);
        } catch (Exception e) { return "unknown"; }
    }
    private String safe(String s) {
        return s == null ? "" : s.length() > 512 ? s.substring(0, 512) : s;
    }
}