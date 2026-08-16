package com.example.service;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.dto.Application;
import com.example.dto.ApplicationRequest;
import com.example.dto.ApplicationStats;
import com.example.dto.ApplicationUpdate;
import com.example.repository.ApplicationRepository;

@Service
public class ApplicationService {

    /** The four pipeline stages the tracker understands. */
    static final Set<String> STATUSES = Set.of("applied", "interviewing", "offer", "rejected");

    private final ApplicationRepository repository;

    public ApplicationService(ApplicationRepository repository) {
        this.repository = repository;
    }

    public List<Application> list(String username) {
        return repository.findByUsername(username);
    }

    public ApplicationStats stats(String username) {
        return repository.stats(username);
    }

    public void create(String username, ApplicationRequest req) {
        if (req == null || (isBlank(req.company()) && isBlank(req.title()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A company or title is required to track an application.");
        }
        String status = isBlank(req.status()) ? "applied" : normalizeStatus(req.status());
        repository.create(username, req, status);
    }

    public void update(String username, String id, ApplicationUpdate update) {
        if (update == null || isBlank(update.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A status is required.");
        }
        String status = normalizeStatus(update.status());
        int rows = repository.updateStatus(username, id, status, update.notes());
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found.");
        }
    }

    public void delete(String username, String id) {
        int rows = repository.delete(username, id);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found.");
        }
    }

    private String normalizeStatus(String status) {
        String s = status.trim().toLowerCase();
        if (!STATUSES.contains(s)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Status must be one of: " + STATUSES);
        }
        return s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
