package com.sagar.linkly.controller;

import com.sagar.linkly.service.ClickEventPublisher;
import com.sagar.linkly.service.ResolverService;
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

    @GetMapping("/{shortCode:[a-zA-Z0-9_-]{1,16}}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest req) {

        // 👻 THE GHOST TRAP: Print every request to the terminal
        System.out.println("--- INCOMING BROWSER REQUEST ---");
        System.out.println("Method: " + req.getMethod());
        System.out.println("User-Agent: " + req.getHeader("User-Agent"));
        System.out.println("Sec-Fetch-Dest: " + req.getHeader("Sec-Fetch-Dest"));
        System.out.println("--------------------------------");

        String longUrl = resolverService.resolve(shortCode);

        // Extract strings
        String ipAddress = req.getRemoteAddr();
        String referrer = req.getHeader("Referer");
        String userAgent = req.getHeader("User-Agent");

        // Grab prefetch headers
        String purpose = req.getHeader("Purpose");
        String secPurpose = req.getHeader("Sec-Purpose");

        // 🔥 NEW RULE: Only count true GET requests, and ignore pre-fetching
        boolean isGetRequest = "GET".equalsIgnoreCase(req.getMethod());
        boolean isPrefetch = "prefetch".equalsIgnoreCase(purpose) || "prefetch".equalsIgnoreCase(secPurpose);

        if (isGetRequest && !isPrefetch) {
            publisher.publish(shortCode, ipAddress, referrer, userAgent);
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate") // Added stricter cache rules
                .build();
    }
}