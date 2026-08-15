package com.example.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.example.dto.Resume;
import com.example.dto.ResumeUploadRequest;
import com.example.dto.TailorRequest;
import com.example.dto.TailorResponse;
import com.example.service.ResumeService;

@RestController
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @GetMapping("/api/resume")
    public ResponseEntity<Resume> getResume(Authentication authentication) {
        return resumeService.get(authentication.getName())
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/resume")
    public Resume saveResume(@RequestBody ResumeUploadRequest request, Authentication authentication) {
        return resumeService.save(authentication.getName(), request);
    }

    @PostMapping("/api/resume/tailor")
    public TailorResponse tailorResume(@RequestBody TailorRequest request, Authentication authentication) {
        return resumeService.tailor(authentication.getName(), request);
    }
}
