package com.example.poller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.boot.CommandLineRunner;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.example.config.PollingTargets;
import com.example.helpers.AtsFetchers;
import com.example.helpers.CsJobFilter;
import com.example.helpers.UsLocationFilter;
import com.example.model.JobBatch;
import com.example.model.JobFound;

@Component
public class JobPoller implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(JobPoller.class);
    private static final Duration BETWEEN_BOARDS = Duration.ofMillis(800);
    private static final Duration MAX_POSTED_AGE = Duration.ofDays(7);

    private final PollingTargets targets;
    private final AtsFetchers fetchers;
    private final RabbitTemplate rabbitTemplate;
    private final long pollIntervalMs;

    public JobPoller(PollingTargets targets, AtsFetchers fetchers, RabbitTemplate rabbitTemplate, @Value("${poll.minutes:30}") long pollMinutes) {
        this.targets = targets;
        this.fetchers = fetchers;
        this.rabbitTemplate = rabbitTemplate;
        this.pollIntervalMs = pollMinutes * 60_000;
    }

    @Override
    public void run(String...args) {

        while (true) {

            Instant startedAt = Instant.now();
            log.info("Poll cycle starting - {} board(s)", targets.getTargets().size());

            List<JobFound> jobHits = new ArrayList<>();
            List<String> jobFails = new ArrayList<>();

            for (PollingTargets.Target t : targets.getTargets()) {
                try {
                    List<JobFound> board = fetchers.fetch(t.getAts(), t.getSlug());
                    List<JobFound> csHits = board.stream().filter(CsJobFilter::isCsJob).toList();
                    List<JobFound> usHits = csHits.stream().filter(j -> UsLocationFilter.isUsa(j.location())).toList();
                    List<JobFound> hits = usHits.stream().filter(JobPoller::isFresh).toList();
                    jobHits.addAll(hits);
                    log.info("   {}: {}/{} CS match, {} US, {} fresh (posted <= {} days ago)", t, csHits.size(), board.size(), usHits.size(), hits.size(), MAX_POSTED_AGE.toDays());
                } catch (Exception e) {
                    
                    String message = t + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage();
                    log.warn(" {}", message);
                    jobFails.add(message);

                }

                try {
                    // Then we sleep between each job board
                    Thread.sleep(BETWEEN_BOARDS.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Poll cycle has been interrupted...publishing what we have");
                    break;
                }
            }

            JobBatch batch = new JobBatch(startedAt, jobHits, jobFails);

            try {
                rabbitTemplate.convertAndSend("job.queue", batch);
                log.info("Poll cycle done in {}s - published {} job(s), {} failure(s)", Duration.between(startedAt, Instant.now()).toSeconds(), jobHits.size(), jobFails.size());
            } catch (Exception e) {
                // Something went wrong with RabbitMQ
                log.error("Failed to publish batch to RabbitMQ", e);
            }

            // Sleep between cycles: POLL_MINUTES env var, defaults to 30
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.info("Scheduler is down...");
                break;
            }
        }
    }

    private static boolean isFresh(JobFound j) {
        Instant posted = parsePosted(j.posted());
        return posted != null && posted.isAfter(Instant.now().minus(MAX_POSTED_AGE));
    }


    private static Instant parsePosted(String posted) {
        if (posted == null || posted.isBlank()) return null;
        String p = posted.strip();

        String lower = p.toLowerCase();
        if (lower.contains("today")) return Instant.now();
        if (lower.contains("yesterday")) return Instant.now().minus(Duration.ofDays(1));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\+? days? ago").matcher(lower);
        if (m.find()) return Instant.now().minus(Duration.ofDays(Long.parseLong(m.group(1))));

        try {
            if (p.matches("\\d{13}")) return Instant.ofEpochMilli(Long.parseLong(p));
            if (p.matches("\\d{10}")) return Instant.ofEpochSecond(Long.parseLong(p));
            return OffsetDateTime.parse(p).toInstant();
        } catch (Exception e) {
            try {
                return LocalDate.parse(p.length() > 10 ? p.substring(0, 10) : p)
                        .atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (Exception e2) {
                return null;
            }
        }
    }
}
