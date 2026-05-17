package com.sagar.curtli.dto;

public record BulkError(
        String longUrl,
        String attemptedAlias,
        String errorMessage
) {}