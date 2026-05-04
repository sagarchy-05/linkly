package com.sagar.linkly.dto;

public record ShortenResponse(
        String shortCode,String shortUrl,
        String longUrl
) {}