package com.sagar.linkly.consumer;

import com.sagar.linkly.repository.LinkRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClickEventConsumer {
    private final StringRedisTemplate redis;
    private final LinkRepository linkRepo;
    private final ClickAggregator aggregator;
    // separate component shown below
    @Value("${linkly.click-stream.name}") private String streamName;
    @Value("${linkly.click-stream.consumer-group}") private String group;
    @Value("${linkly.click-stream.consumer-name}") private String consumer;

    @PostConstruct
    public void initGroup() {
        try {
            redis.opsForStream().createGroup(streamName, ReadOffset.from("0"), group);
        } catch (Exception ignored) { /* group already exists */ }
    }

    @Scheduled(fixedDelay = 1000)
    public void consume() {
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                Consumer.from(group, consumer),
                StreamReadOptions.empty().count(200).block(Duration.ofMillis(500)),
                StreamOffset.create(streamName, ReadOffset.lastConsumed())
        );
        if (records == null || records.isEmpty()) return;
        Map<String, Long> codeToCount = new HashMap<>();
        for (MapRecord<String, Object, Object> r : records) {
            String code = String.valueOf(r.getValue().get("shortCode"));
            codeToCount.merge(code, 1L, Long::sum);
        }
        aggregator.flush(codeToCount);
        // Acknowledge
        for (MapRecord<String, Object, Object> r : records) {
            redis.opsForStream().acknowledge(streamName, group, r.getId());
        }
    }
}