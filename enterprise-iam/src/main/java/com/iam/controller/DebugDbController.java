package com.iam.controller;

import com.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth/debug/db")
@RequiredArgsConstructor
public class DebugDbController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public List<String> inspectUsers() {
        return userRepository.findAll().stream()
                .map(u -> "id: " + u.getId() + ", username: " + u.getUsername() + ", email: " + u.getEmail() + ", enabled: " + u.isEnabled() + ", verified: " + u.isEmailVerified() + ", passwordHash: " + u.getPasswordHash())
                .collect(Collectors.toList());
    }

    @GetMapping("/verify-password")
    public String verifyPassword(@RequestParam String raw, @RequestParam String hash) {
        boolean matches = passwordEncoder.matches(raw, hash);
        if (matches) {
            return "PASSWORD MATCHES: The password '" + raw + "' is CORRECT for that hash!";
        } else {
            String newHash = passwordEncoder.encode(raw);
            return "PASSWORD DOES NOT MATCH. A fresh hash for '" + raw + "' would be: " + newHash;
        }
    }
}
