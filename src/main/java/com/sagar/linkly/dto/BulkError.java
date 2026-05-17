package com.sagar.linkly.dto;

public record BulkError(
        String longUrl,
        String attemptedAlias,
        String errorMessage
) {}