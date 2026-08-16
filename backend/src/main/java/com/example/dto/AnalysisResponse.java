package com.example.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Phase-one result shown BEFORE tailoring: a confidence score plus the posting's
 * requirements, each flagged as already-matched by the resume or still missing.
 * Also the structured-output schema the model fills in.
 */
@JsonClassDescription("A resume-to-job confidence score and a requirement-by-requirement gap analysis.")
public record AnalysisResponse(

    @JsonPropertyDescription(
        "Integer from 0 to 100 for how closely the candidate's CURRENT resume "
      + "matches this posting. Low means far apart; high means very similar. "
      + "Base it on overlap of skills, experience, seniority, and domain.")
    int score,

    @JsonPropertyDescription(
        "One or two sentences explaining the score: the biggest strengths and "
      + "the biggest gaps.")
    String rationale,

    @JsonPropertyDescription(
        "The concrete requirements pulled from the posting (tech stack, tools, "
      + "years/level of experience, education). Include the most important 6-14 "
      + "requirements. For each, set matched=true only if the current resume "
      + "clearly evidences it. Order roughly by importance.")
    List<SkillRequirement> requirements
) {}
