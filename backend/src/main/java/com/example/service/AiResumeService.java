package com.example.service;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.example.dto.AnalysisResponse;
import com.example.dto.SkillRequirement;
import com.example.dto.TailorRequest;
import com.example.dto.TailorResponse;

/**
 * Wraps the two Claude calls the resume feature makes: converting an uploaded
 * PDF into Markdown, and tailoring the stored resume to a specific posting.
 * The Anthropic client is built lazily so the backend still boots (and the rest
 * of the dashboard works) when no ANTHROPIC_API_KEY is configured.
 */
@Service
public class AiResumeService {

    private static final Logger log = LoggerFactory.getLogger(AiResumeService.class);
    private static final String MODEL = "claude-opus-5";

    private static final String ENTRY_FORMAT = """
        For every Work Experience and Education entry, format its header as exactly
        two lines using ' | ' (space pipe space) as a column separator:
          Line 1: **Company or School** | **Start – End**
          Line 2: Role or Degree | Location
        Wrap the company/school AND the dates in ** (bold); leave the role/degree
        and location un-bolded. Put the entry's bullet points after these two
        lines. Use this format only for those entries, not for other sections.

        Use a level-1 heading ('# ') for the candidate's name at the very top,
        followed by their contact details (email, GitHub, LinkedIn, etc.), and a
        level-2 heading ('## ') for every section (Experience, Education, Skills).""";

    private static final String EXTRACT_PROMPT = """
        Convert this resume into clean Markdown. Preserve the section headings,
        their order, and the bullet content faithfully — do not add, remove, or
        summarize experience.

        %s

        Output only the Markdown, with no preamble or commentary.""".formatted(ENTRY_FORMAT);

    private static final String TAILOR_SYSTEM = """
        You are an expert technical resume writer and recruiter. You are given a
        candidate's current resume (Markdown) and a software-engineering job
        posting. Produce a match score and a tailored resume.

        Treat the candidate's original resume as the exact FORMATTING TEMPLATE:
        the tailored resume must be visually indistinguishable from it in layout,
        so the two can be compared side by side.

        Rules for the tailored resume:
        - Mirror the original's structure EXACTLY: the same heading levels, the
          same section titles and the same section order, the same contact-line
          format, and the same entry-header layout. Do not add, drop, rename, or
          reorder sections that exist in the original.
        - Keep the SAME proportions: for each entry, keep roughly the same number
          of bullet points as the original (do not expand 2 bullets into 6), and
          keep each bullet to a similar length. Only rephrase the existing content
          toward the posting — do not pad it out.
        - Keep it to ONE page (about 600 words or fewer), matching the original's
          overall length.
        - Stay strictly truthful: rephrase and reprioritize the candidate's real
          experience and skills toward this posting. Never invent employers,
          titles, dates, degrees, or skills the candidate does not have.
        - Surface the experience and keywords most relevant to the posting first
          within each section.

        The score is 0-100 for how closely the ORIGINAL (untailored) resume
        already matches the posting.

        %s""".formatted(ENTRY_FORMAT);

    private static final String ANALYZE_SYSTEM = """
        You are an expert technical recruiter. You are given a candidate's current
        resume (Markdown) and a software-engineering job posting. Do NOT rewrite
        the resume. Instead:

        1. Give a 0-100 confidence score for how closely the candidate's CURRENT
           resume already matches the posting (low = far apart, high = very close).
        2. Extract the posting's concrete requirements — programming languages,
           frameworks, tools, cloud/infra, years and level of experience, and
           education. Pick the 6-14 most important.
        3. For each requirement, decide matched=true ONLY when the current resume
           clearly evidences it; otherwise matched=false. Add a short note: where
           the resume shows it, or what is missing.

        Be strict and truthful — do not mark something matched on a weak or absent
        signal.""";

    private volatile AnthropicClient client;

    /** Build (once) or fail loudly with a helpful 503 if the key is missing. */
    private AnthropicClient client() {
        AnthropicClient c = client;
        if (c != null) return c;
        synchronized (this) {
            if (client != null) return client;
            String key = System.getenv("ANTHROPIC_API_KEY");
            if (key == null || key.isBlank()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Resume AI features are unavailable: set ANTHROPIC_API_KEY on the backend.");
            }
            client = AnthropicOkHttpClient.builder().apiKey(key).build();
            return client;
        }
    }

    /** Turn an uploaded PDF (base64, no data-URI prefix) into Markdown text. */
    public String pdfToMarkdown(String base64Pdf) {
        DocumentBlockParam doc = DocumentBlockParam.builder()
            .source(Base64PdfSource.builder().data(base64Pdf).build())
            .build();

        MessageCreateParams params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(12_000L)
            .addUserMessageOfBlockParams(List.of(
                ContentBlockParam.ofDocument(doc),
                ContentBlockParam.ofText(TextBlockParam.builder().text(EXTRACT_PROMPT).build())))
            .build();

        try {
            String md = client().messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .collect(Collectors.joining("\n"))
                .strip();
            if (md.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not read any text from the uploaded PDF.");
            }
            return md;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("PDF extraction failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Failed to read the PDF: " + e.getMessage());
        }
    }

    /**
     * Phase one: score the resume and break the posting into requirements, each
     * flagged as already-matched or missing. No rewriting happens here.
     */
    public AnalysisResponse analyze(String resume, TailorRequest job, String jobDescription) {

        StructuredMessageCreateParams<AnalysisResponse> params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(8_000L)
            .system(ANALYZE_SYSTEM)
            .addUserMessage(buildUserPrompt(resume, job, jobDescription))
            .outputConfig(AnalysisResponse.class)
            .build();

        try {
            AnalysisResponse result = client().messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The model returned no analysis."));

            int score = Math.max(0, Math.min(100, result.score()));
            List<SkillRequirement> reqs = result.requirements() == null ? List.of() : result.requirements();
            return new AnalysisResponse(score, result.rationale(), reqs);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Resume analysis failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Failed to analyze the resume: " + e.getMessage());
        }
    }

    /** Score + tailor {@code resume} against one posting. */
    public TailorResponse tailor(String resume, TailorRequest job, String jobDescription) {

        StructuredMessageCreateParams<TailorResponse> params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(20_000L)
            .system(TAILOR_SYSTEM)
            .addUserMessage(buildUserPrompt(resume, job, jobDescription))
            .outputConfig(TailorResponse.class)
            .build();

        try {
            TailorResponse result = client().messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The model returned no tailored resume."));

            int score = Math.max(0, Math.min(100, result.score()));
            return new TailorResponse(score, result.rationale(), result.tailoredResume());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Resume tailoring failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Failed to tailor the resume: " + e.getMessage());
        }
    }

    /** Shared prompt body: the candidate resume plus the posting metadata + description. */
    private static String buildUserPrompt(String resume, TailorRequest job, String jobDescription) {
        String jd = (jobDescription == null || jobDescription.isBlank())
            ? "(The full description could not be retrieved. Use the job metadata below.)"
            : jobDescription;

        return """
            === CANDIDATE RESUME (Markdown) ===
            %s

            === JOB POSTING ===
            Title: %s
            Company: %s
            Location: %s
            Department: %s
            URL: %s

            Description:
            %s
            """.formatted(
                resume,
                nz(job.title()), nz(job.company()), nz(job.location()),
                nz(job.department()), nz(job.url()), jd);
    }

    private static String nz(String s) {
        return (s == null || s.isBlank()) ? "N/A" : s;
    }
}
