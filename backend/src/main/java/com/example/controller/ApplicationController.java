package com.example.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.example.dto.Application;
import com.example.dto.ApplicationRequest;
import com.example.dto.ApplicationStats;
import com.example.dto.ApplicationUpdate;
import com.example.service.ApplicationService;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public List<Application> list(Authentication auth) {
        return service.list(auth.getName());
    }

    @GetMapping("/stats")
    public ApplicationStats stats(Authentication auth) {
        return service.stats(auth.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@RequestBody ApplicationRequest request, Authentication auth) {
        service.create(auth.getName(), request);
    }

    @PatchMapping("/{id}")
    public void update(@PathVariable String id, @RequestBody ApplicationUpdate update, Authentication auth) {
        service.update(auth.getName(), id, update);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, Authentication auth) {
        service.delete(auth.getName(), id);
    }
}
