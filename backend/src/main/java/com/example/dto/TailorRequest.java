package com.example.dto;

/**
 * The job the user wants their resume tailored to. These are exactly the fields
 * the dashboard already holds for a posting; the backend fetches the full job
 * description from {@code url} before calling the model.
 */
public record TailorRequest(
    String company,
    String ats,
    String title,
    String location,
    String department,
    String url
) {}
