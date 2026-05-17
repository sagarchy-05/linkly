package com.sagar.curtli.dto;

public record ShortenResponse(
        String shortCode,String shortUrl,
        String longUrl
) {}