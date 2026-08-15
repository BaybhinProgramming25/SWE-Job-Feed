package com.example.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dto.User;
import com.example.repository.SubscribeRepository;
import com.example.repository.UserRepository;

import java.util.List;

@Service
public class SubscribeService {

    // Every ATS the scheduler polls. New users are auto-subscribed to all of
    // them so the feed is populated without any setup. Must match the fetcher
    // case labels (and the job's `ats` field) in the scheduler.
    public static final List<String> DEFAULT_ATS = List.of(
        "greenhouse", "lever", "ashby", "smartrecruiters", "recruitee",
        "workable", "teamtailor", "workday", "remotive", "remoteok", "arbeitnow");

    private final SubscribeRepository subscribeRepository;
    private final UserRepository userRepository;

    public SubscribeService(SubscribeRepository subscribeRepository, UserRepository userRepository) {
        this.subscribeRepository = subscribeRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void subscribeToCompanies(String username, List<String> companies) {

        User user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalStateException("User not found: " + username));
        subscribeRepository.saveUserCompanies(user.id(), companies);
    }

    /** Subscribe a user to every ATS — the default for new accounts. */
    public void subscribeToDefaults(String username) {
        subscribeToCompanies(username, DEFAULT_ATS);
    }
}
