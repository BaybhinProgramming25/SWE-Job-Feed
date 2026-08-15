package com.example.reconcile;

import com.example.helpers.UsLocationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One-shot on startup: purges rows in found_jobs whose stored location no
 * longer passes the US-only geo gate. This lets a tightening of
 * {@link UsLocationFilter} also evict jobs that were ingested under looser
 * rules, instead of waiting for them to age out. Runs before the poller.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StartupUsaReconciler implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupUsaReconciler.class);

    private final JdbcTemplate jdbc;

    public StartupUsaReconciler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {

        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(
                "SELECT jobId AS job_id, location AS loc FROM dist_jobs_scheduler.found_jobs");
        } catch (Exception e) {
            log.warn("USA reconcile skipped - could not read found_jobs: {}", e.getMessage());
            return;
        }

        List<String> stale = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object loc = row.get("loc");
            if (!UsLocationFilter.isUsa(loc == null ? "" : loc.toString())) {
                stale.add((String) row.get("job_id"));
            }
        }

        if (stale.isEmpty()) {
            log.info("USA reconcile: all {} stored job(s) are US-based, nothing to purge", rows.size());
            return;
        }

        int deleted = 0;
        for (String jobId : stale) {
            deleted += jdbc.update(
                "DELETE FROM dist_jobs_scheduler.found_jobs WHERE jobId = ?", jobId);
        }
        log.info("USA reconcile: purged {} non-US job(s) of {} stored", deleted, rows.size());
    }
}
