package com.example.dto;

/** Payload to start tracking a job (from the feed or entered manually). */
public record ApplicationRequest(
    String company,
    String title,
    String location,
    String url,
    String ats,
    String status,   // optional; defaults to "applied"
    String notes
) {}
