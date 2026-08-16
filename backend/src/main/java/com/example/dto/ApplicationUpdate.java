package com.example.dto;

/** Payload to move an application to a new status (and optionally edit notes). */
public record ApplicationUpdate(
    String status,
    String notes
) {}
