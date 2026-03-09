package com.ask2watch.service;

import com.ask2watch.dto.auth.AuthResponse;
import com.ask2watch.dto.auth.LoginRequest;
import com.ask2watch.dto.auth.RegisterRequest;
import com.ask2watch.exception.AuthenticationException;
import com.ask2watch.exception.DuplicateResourceException;
import com.ask2watch.model.User;
import com.ask2watch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;

    public AuthResponse login(LoginRequest request, String clientIp) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    auditLogService.logLoginFailure(request.getEmail(), clientIp);
                    return new AuthenticationException("Invalid credentials");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            auditLogService.logLoginFailure(request.getEmail(), clientIp);
            throw new AuthenticationException("Invalid credentials");
        }

        auditLogService.logLoginSuccess(request.getEmail(), clientIp);
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getUsername());
    }

    public AuthResponse register(RegisterRequest request, String clientIp) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already in use");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        user = userRepository.save(user);
        auditLogService.logRegistration(request.getEmail(), request.getUsername(), clientIp);
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getUsername());
    }
}
