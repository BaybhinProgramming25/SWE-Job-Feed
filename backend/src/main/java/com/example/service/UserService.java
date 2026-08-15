package com.example.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.dto.User;
import com.example.dto.LoginRequest;
import com.example.dto.LoginResponse;
import com.example.dto.SignupRequest;
import com.example.dto.SignupResponse;
import com.example.repository.UserRepository;

@Service 
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SubscribeService subscribeService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, SubscribeService subscribeService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.subscribeService = subscribeService;
    }

    public SignupResponse signup(SignupRequest request) {

        if (
            request.username() == null || request.username().isBlank() ||
            request.email() == null || request.email().isBlank() || 
            request.password() == null || request.password().length() < 8 || 
            request.phoneNumber() == null || request.phoneNumber().isBlank()
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username, email, phone number required. password must be at least 8 characters.");
        }

        if (userRepository.existsByUsernameOrEmail(request.username(), request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username or email already taken.");
        }

        String hash = passwordEncoder.encode(request.password());
        User saved = userRepository.save(request.username(), request.email(), hash, request.phoneNumber());

        // Auto-subscribe new accounts to every ATS so the feed is populated.
        subscribeService.subscribeToDefaults(saved.username());

        String token = jwtService.generateToken(saved.username());
        return new SignupResponse(saved.id(), saved.username(), saved.email(), token);
    }

    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByUsername(request.username()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.passwordHashed())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid username or password");
        }

        String token = jwtService.generateToken(user.username());
        return new LoginResponse(token, user.username());
    }
}
