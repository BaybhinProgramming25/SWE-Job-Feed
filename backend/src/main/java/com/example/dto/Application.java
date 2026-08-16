package com.example.dto;

import java.time.OffsetDateTime;

/** One tracked job application in the user's pipeline. */
public record Application(
    String id,
    String company,
    String title,
    String location,
    String url,
    String ats,
    String status,
    String notes,
    OffsetDateTime appliedAt,
    OffsetDateTime updatedAt
) {}
