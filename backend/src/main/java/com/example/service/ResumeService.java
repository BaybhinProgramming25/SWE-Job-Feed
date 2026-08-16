package com.example.service;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.dto.AnalysisResponse;
import com.example.dto.Resume;
import com.example.dto.ResumeUploadRequest;
import com.example.dto.TailorRequest;
import com.example.dto.TailorResponse;
import com.example.repository.ResumeRepository;

@Service
public class ResumeService {

    private final ResumeRepository repository;
    private final AiResumeService ai;
    private final JobDescriptionFetcher jobDescriptions;

    public ResumeService(ResumeRepository repository, AiResumeService ai,
                         JobDescriptionFetcher jobDescriptions) {
        this.repository = repository;
        this.ai = ai;
        this.jobDescriptions = jobDescriptions;
    }

    /** Store a resume: convert a PDF to Markdown first, or take pasted text as-is. */
    public Resume save(String username, ResumeUploadRequest request) {

        String content;
        if (request.pdfBase64() != null && !request.pdfBase64().isBlank()) {
            content = ai.pdfToMarkdown(stripDataUri(request.pdfBase64()));
        } else if (request.content() != null && !request.content().isBlank()) {
            content = request.content().strip();
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Provide resume text or a PDF to import.");
        }

        repository.save(username, content, request.filename());
        return repository.findByUsername(username).orElseThrow();
    }

    public Optional<Resume> get(String username) {
        return repository.findByUsername(username);
    }

    /** Phase one: score + requirement gap analysis, without rewriting the resume. */
    public AnalysisResponse analyze(String username, TailorRequest job) {

        Resume resume = repository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Import a resume before analyzing it against a job."));

        String jd = jobDescriptions.fetch(job.url());
        return ai.analyze(resume.content(), job, jd);
    }

    /** Fetch the posting text and tailor the user's stored resume to it. */
    public TailorResponse tailor(String username, TailorRequest job) {

        Resume resume = repository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Import a resume before tailoring it to a job."));

        String jd = jobDescriptions.fetch(job.url());
        return ai.tailor(resume.content(), job, jd);
    }

    /** Browsers send PDFs as a data URI (`data:application/pdf;base64,XXXX`). */
    private static String stripDataUri(String value) {
        int comma = value.indexOf(',');
        String base64 = (value.startsWith("data:") && comma >= 0) ? value.substring(comma + 1) : value;
        return base64.replaceAll("\\s", "");
    }
}
