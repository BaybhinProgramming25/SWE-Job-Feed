package com.example.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Best-effort retrieval of a posting's full text. Fetches the job URL and strips
 * it down to readable text. Some ATSes render the description with JavaScript,
 * so a fetch can come back thin; callers should fall back to the structured job
 * metadata (title, company, location) they already have when that happens.
 */
@Service
public class JobDescriptionFetcher {

    private static final Logger log = LoggerFactory.getLogger(JobDescriptionFetcher.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_CHARS = 12_000;

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    /** Returns extracted posting text, or an empty string if nothing usable was found. */
    public String fetch(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (compatible; swe-job-feed resume tailor)")
                .header("Accept", "text/html,application/xhtml+xml")
                .timeout(TIMEOUT)
                .GET().build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                log.warn("Job description fetch got HTTP {} from {}", resp.statusCode(), url);
                return "";
            }
            return stripHtml(resp.body());
        } catch (Exception e) {
            log.warn("Could not fetch job description from {}: {}", url, e.getMessage());
            return "";
        }
    }

    private static String stripHtml(String html) {
        if (html == null) return "";
        String text = html
            .replaceAll("(?is)<script.*?</script>", " ")
            .replaceAll("(?is)<style.*?</style>", " ")
            .replaceAll("(?is)<head.*?</head>", " ")
            .replaceAll("(?is)<!--.*?-->", " ")
            .replaceAll("(?is)<br\\s*/?>", "\n")
            .replaceAll("(?is)</(p|div|li|h[1-6]|tr)>", "\n")
            .replaceAll("(?s)<[^>]+>", " ");

        text = text
            .replace("&nbsp;", " ").replace("&amp;", "&")
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace("&#39;", "'").replace("&quot;", "\"")
            .replaceAll("[ \\t]+", " ")
            .replaceAll("\\n\\s*\\n\\s*\\n+", "\n\n")
            .strip();

        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }
}
