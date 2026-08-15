package com.example.repository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.dto.JobFound;

@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<JobFound> findJobsWatchedBy(String username) {

        return jdbc.query("""
                SELECT f.company, f.ats, f.title, f.location, f.department, f.url, f.posted, f.firstSeen
                FROM dist_jobs_scheduler.found_jobs f
                JOIN dist_jobs_scheduler.watched_companies w ON w.company_name = f.ats
                JOIN dist_jobs_scheduler.users u ON u.id = w.user_id
                WHERE u.username = ?
                ORDER BY
                    CASE
                        -- Senior and above sink to the bottom.
                        WHEN f.title ~* '\\b(senior|sr|staff|principal|lead|manager|director|vp|head|chief|distinguished|architect|fellow)\\b'
                            THEN 2
                        -- Explicit junior/mid signals float to the top.
                        WHEN f.title ~* '\\b(junior|jr|entry|associate|new[ -]?grad|graduate|early career|apprentice)\\b'
                          OR f.title ~* '(software engineer|swe)[^a-z0-9]+(i{1,3}|[1-3])\\b'
                            THEN 0
                        -- Everything else (e.g. plain "Software Engineer") in the middle.
                        ELSE 1
                    END,
                    f.firstSeen DESC
                """,
                this::mapRow,
                username
            );
    }

    private JobFound mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {

        return new JobFound(
            rs.getString("company"),
            rs.getString("ats"),
            rs.getString("title"),
            rs.getString("location"),
            rs.getString("department"),
            rs.getString("url"),
            rs.getString("posted"),
            rs.getObject("firstSeen", java.time.OffsetDateTime.class)
        );
    }
}
