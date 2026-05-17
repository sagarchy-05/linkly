package com.sagar.curtli.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShortenRequest(
        @NotBlank @Size(max = 2048) String longUrl,
        String customAlias,
        @Min(value = 1, message = "expiresInDays must be at least 1")
        @Max(value = 3650, message = "expiresInDays must be at most 3650 (10 years)")
        Long expiresInDays
) {}