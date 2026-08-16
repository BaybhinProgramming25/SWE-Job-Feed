package com.example.dto;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * One requirement extracted from a job posting, paired with whether the
 * candidate's current resume already demonstrates it. Doubles as part of the
 * structured-output schema the model fills in (hence the Jackson descriptions).
 */
@JsonClassDescription("A single job requirement and whether the resume already covers it.")
public record SkillRequirement(

    @JsonPropertyDescription(
        "The requirement as a short label, e.g. \"React\", \"Kubernetes\", "
      + "\"5+ years backend experience\", \"BS in Computer Science\".")
    String skill,

    @JsonPropertyDescription(
        "One of: language, framework, tool, cloud, experience, education, "
      + "domain, other.")
    String category,

    @JsonPropertyDescription(
        "true if the candidate's CURRENT resume already clearly demonstrates "
      + "this requirement; false if it is missing or not evidenced.")
    boolean matched,

    @JsonPropertyDescription(
        "If matched, briefly where/how the resume shows it. If missing, a short "
      + "note on what the candidate would need to add or emphasize.")
    String note
) {}
