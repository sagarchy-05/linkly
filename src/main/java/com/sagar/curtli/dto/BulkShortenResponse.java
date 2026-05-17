package com.sagar.curtli.dto;

import java.util.List;

public record BulkShortenResponse(
        List<ShortenResponse> successful,
        List<BulkError> failed
) {}