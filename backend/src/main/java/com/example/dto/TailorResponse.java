package com.example.dto;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Result of tailoring the stored resume to one job posting. Also doubles as the
 * structured-output schema the model fills in (hence the Jackson descriptions).
 */
@JsonClassDescription("A resume-to-job match score and a tailored one-page resume.")
public record TailorResponse(

    @JsonPropertyDescription(
        "Integer from 0 to 100 for how closely the ORIGINAL resume matches this "
      + "job posting. A low score means they are far apart; a high score means "
      + "they are very similar. Base it on overlap of skills, experience, "
      + "seniority, and domain.")
    int score,

    @JsonPropertyDescription(
        "One or two sentences explaining the score: the biggest matches and the "
      + "biggest gaps.")
    String rationale,

    @JsonPropertyDescription(
        "The tailored resume in Markdown. It MUST be visually indistinguishable "
      + "in FORMAT from the original resume: the same heading levels, the same "
      + "section titles and order, the same contact-line and entry-header layout, "
      + "and roughly the same number and length of bullet points per entry (do "
      + "not expand or pad). It MUST fit on one page (roughly 600 words or fewer) "
      + "and stay truthful — rephrase and reprioritize the candidate's real "
      + "experience toward this posting, never invent employers, titles, dates, "
      + "or skills.")
    String tailoredResume
) {}
