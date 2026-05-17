package com.sagar.linkly.controller;

import com.sagar.linkly.dto.BulkShortenResponse;
import com.sagar.linkly.dto.ShortenRequest;
import com.sagar.linkly.dto.ShortenResponse;
import com.sagar.linkly.service.ShortenerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ShortenerController {
    private final ShortenerService service;

    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest req) {
        return ResponseEntity.ok(service.shorten(req));
    }

    @PostMapping("/bulk-shorten")
    public ResponseEntity<BulkShortenResponse> bulk(@Valid @RequestBody List<ShortenRequest> reqs) {
        BulkShortenResponse response = service.bulkShorten(reqs);

        // If everything failed
        if (response.successful().isEmpty() && !response.failed().isEmpty()) {
            return ResponseEntity.badRequest().body(response);
        }

        // If it's a mix or all successful
        return ResponseEntity.ok(response);
    }
}