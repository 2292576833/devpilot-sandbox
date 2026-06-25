package com.devpilot.sandbox.controller;

import com.devpilot.sandbox.config.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")

public class AuthController {

    @Value("${sandbox.auth.username:admin}")
    private String authUsername;

    @Value("${sandbox.auth.password:admin}")
    private String authPassword;

    @Autowired
    private JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        Map<String, Object> result = new HashMap<>();
        String username = credentials.get("username");
        String password = credentials.get("password");
        if (authUsername.equals(username) && authPassword.equals(password)) {
            String token = jwtService.createToken(username);
            if (token == null) {
                result.put("error", "Failed to create token");
                return ResponseEntity.status(500).body(result);
            }
            result.put("token", token);
            result.put("tokenType", "Bearer");
            result.put("expiresIn", 86400);
            return ResponseEntity.ok(result);
        }
        result.put("error", "Invalid credentials");
        return ResponseEntity.status(401).body(result);
    }
}