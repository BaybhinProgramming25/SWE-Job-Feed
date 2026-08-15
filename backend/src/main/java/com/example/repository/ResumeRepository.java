package com.example.repository;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.dto.Resume;

@Repository
public class ResumeRepository {

    private final JdbcTemplate jdbc;

    public ResumeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** One resume per user; a second save overwrites the first. */
    public void save(String username, String content, String filename) {

        jdbc.update("""
                INSERT INTO dist_jobs_scheduler.resumes (user_id, content, filename, updated_at)
                SELECT id, ?, ?, now() FROM dist_jobs_scheduler.users WHERE username = ?
                ON CONFLICT (user_id) DO UPDATE
                    SET content = excluded.content,
                        filename = excluded.filename,
                        updated_at = now()
                """,
                content, filename, username);
    }

    public Optional<Resume> findByUsername(String username) {

        return jdbc.query("""
                SELECT r.content, r.filename, r.updated_at
                FROM dist_jobs_scheduler.resumes r
                JOIN dist_jobs_scheduler.users u ON u.id = r.user_id
                WHERE u.username = ?
                """,
                this::mapRow,
                username)
            .stream().findFirst();
    }

    private Resume mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Resume(
            rs.getString("content"),
            rs.getString("filename"),
            rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
}
