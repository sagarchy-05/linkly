package com.sagar.curtli.controller;

import com.sagar.curtli.service.ClickDebouncer;
import com.sagar.curtli.service.ClickEventPublisher;
import com.sagar.curtli.service.ResolverService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class RedirectController {
    private final ResolverService resolverService;
    private final ClickEventPublisher publisher;
    private final ClickDebouncer debouncer;

    @GetMapping("/{shortCode:[a-zA-Z0-9_-]{1,16}}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest req) {
        String longUrl = resolverService.resolve(shortCode);
        String ipAddress = req.getRemoteAddr();

        // Always redirect, but only publish if it passes the debounce check
        if (debouncer.isUniqueClick(shortCode, ipAddress)) {
            publisher.publish(shortCode, ipAddress, req.getHeader("Referer"), req.getHeader("User-Agent"));
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}