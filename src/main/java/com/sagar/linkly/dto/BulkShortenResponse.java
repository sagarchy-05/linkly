package com.sagar.linkly.dto;

import java.util.List;

public record BulkShortenResponse(
        List<ShortenResponse> successful,
        List<BulkError> failed
) {}