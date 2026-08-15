package com.example.dto;

import java.time.OffsetDateTime;

public record Resume(
    String content,
    String filename,
    OffsetDateTime updatedAt
) {}
