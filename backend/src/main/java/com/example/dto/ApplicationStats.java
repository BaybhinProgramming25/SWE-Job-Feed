package com.example.dto;

/** Headline counts for the tracker dashboard. */
public record ApplicationStats(
    int totalLifetime,   // every job ever tracked
    int appliedToday,    // tracked today
    int interviewing,    // current status = interviewing
    int offers,          // current status = offer
    int rejected         // current status = rejected
) {}
