package com.example.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.dto.Application;
import com.example.dto.ApplicationRequest;
import com.example.dto.ApplicationStats;

@Repository
public class ApplicationRepository {

    private final JdbcTemplate jdbc;

    public ApplicationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Insert a new tracked application for the given user. */
    public void create(String username, ApplicationRequest req, String status) {
        jdbc.update("""
                INSERT INTO dist_jobs_scheduler.applications
                    (user_id, company, title, location, url, ats, status, notes)
                SELECT id, ?, ?, ?, ?, ?, ?, ?
                FROM dist_jobs_scheduler.users WHERE username = ?
                """,
                req.company(), req.title(), req.location(), req.url(), req.ats(),
                status, req.notes(), username);
    }

    public List<Application> findByUsername(String username) {
        return jdbc.query("""
                SELECT a.id, a.company, a.title, a.location, a.url, a.ats,
                       a.status, a.notes, a.applied_at, a.updated_at
                FROM dist_jobs_scheduler.applications a
                JOIN dist_jobs_scheduler.users u ON u.id = a.user_id
                WHERE u.username = ?
                ORDER BY a.applied_at DESC
                """,
                this::mapRow, username);
    }

    /** Update status/notes, scoped to the owner so users can't touch others' rows. */
    public int updateStatus(String username, String id, String status, String notes) {
        return jdbc.update("""
                UPDATE dist_jobs_scheduler.applications
                SET status = ?, notes = ?, updated_at = now()
                WHERE id = ?
                  AND user_id = (SELECT id FROM dist_jobs_scheduler.users WHERE username = ?)
                """,
                status, notes, java.util.UUID.fromString(id), username);
    }

    public int delete(String username, String id) {
        return jdbc.update("""
                DELETE FROM dist_jobs_scheduler.applications
                WHERE id = ?
                  AND user_id = (SELECT id FROM dist_jobs_scheduler.users WHERE username = ?)
                """,
                java.util.UUID.fromString(id), username);
    }

    public ApplicationStats stats(String username) {
        return jdbc.queryForObject("""
                SELECT
                    count(*)                                                   AS total,
                    count(*) FILTER (WHERE a.applied_at::date = current_date)  AS today,
                    count(*) FILTER (WHERE a.status = 'interviewing')          AS interviewing,
                    count(*) FILTER (WHERE a.status = 'offer')                 AS offers,
                    count(*) FILTER (WHERE a.status = 'rejected')              AS rejected
                FROM dist_jobs_scheduler.applications a
                JOIN dist_jobs_scheduler.users u ON u.id = a.user_id
                WHERE u.username = ?
                """,
                (rs, n) -> new ApplicationStats(
                    rs.getInt("total"), rs.getInt("today"), rs.getInt("interviewing"),
                    rs.getInt("offers"), rs.getInt("rejected")),
                username);
    }

    private Application mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Application(
            rs.getString("id"),
            rs.getString("company"),
            rs.getString("title"),
            rs.getString("location"),
            rs.getString("url"),
            rs.getString("ats"),
            rs.getString("status"),
            rs.getString("notes"),
            rs.getObject("applied_at", java.time.OffsetDateTime.class),
            rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
}
