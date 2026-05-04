package com.sagar.linkly.consumer;

import com.sagar.linkly.domain.Link;
import com.sagar.linkly.repository.ClickStatRepository;
import com.sagar.linkly.repository.LinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClickAggregator {

    private final LinkRepository linkRepository;
    private final ClickStatRepository clickStatRepository;

    @Transactional
    public void flush(Map<String, Long> codeToCount) {
        if (codeToCount == null || codeToCount.isEmpty()) {
            return;
        }

        // Calculate the current "bucket hour" by truncating the current time to the hour
        OffsetDateTime currentHourBucket = OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS);

        for (Map.Entry<String, Long> entry : codeToCount.entrySet()) {
            String shortCode = entry.getKey();
            Long newClicks = entry.getValue();

            try {
                Optional<Link> linkOpt = linkRepository.findByShortCodeAndActiveTrue(shortCode);

                if (linkOpt.isPresent()) {
                    Long linkId = linkOpt.get().getId();

                    // 1. Update the all-time total count on the links table
                    linkRepository.incrementClickCount(linkId, newClicks);

                    // 2. Update the time-series analytics on the click_stats table
                    clickStatRepository.upsertClickCount(linkId, currentHourBucket, newClicks);

                } else {
                    log.warn("Aggregator: Link not found or inactive for code: {}", shortCode);
                }
            } catch (Exception e) {
                log.error("Failed to aggregate clicks for code {}: {}", shortCode, e.getMessage());
            }
        }
    }
}