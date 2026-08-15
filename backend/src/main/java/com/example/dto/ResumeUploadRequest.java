package com.example.dto;

/**
 * Import payload. Either paste raw text/Markdown in {@code content}, or upload a
 * PDF as base64 in {@code pdfBase64} (the backend converts it to Markdown once
 * via Claude before storing). {@code filename} is optional metadata.
 */
public record ResumeUploadRequest(
    String content,
    String pdfBase64,
    String filename
) {}
