package com.chat.controller;

import com.chat.dto.LoginRequest;
import com.chat.dto.LoginResponse;
import com.chat.dto.UserInfo;
import com.chat.model.AuthUser;
import com.chat.service.JwtService;
import com.chat.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Authentication Controller
 * 
 * Responsibility: Provides REST endpoint for user login and JWT token issuance.
 * Does NOT: Validate passwords (see UserService), generate tokens (see JwtService), handle gRPC authentication.
 * 
 * Distributed Systems Concept: Separates authentication protocol (REST) from application protocol (gRPC).
 * Login via HTTP POST enables web/mobile clients to obtain JWT token, then use token in gRPC metadata
 * for subsequent service calls. Demonstrates protocol-agnostic authentication.
 * 
 * Endpoints:
 * - POST /api/auth/login: Authenticate user and return JWT token
 * 
 * See: specs/001-ubiquitous-messaging-platform/authentication-jwt-data-model.md (API Endpoints section)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private JwtService jwtService;
    
    @Value("${jwt.expiration}")
    private Long jwtExpiration;
    
    /**
     * Login endpoint - authenticates user and returns JWT token.
     * 
     * Request Body:
     * {
     *   "username": "alice",
     *   "password": "password123"
     * }
     * 
     * Success Response (200 OK):
     * {
     *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
     *   "tokenType": "Bearer",
     *   "expiresIn": 86400000,
     *   "user": {
     *     "userId": "a1a1a1a1-1111-1111-1111-111111111111",
     *     "username": "alice",
     *     "role": "ROLE_USER"
     *   }
     * }
     * 
     * Error Response (401 Unauthorized):
     * {
     *   "error": "Unauthorized",
     *   "message": "Invalid username or password",
     *   "timestamp": "2025-11-23T20:00:00.000Z"
     * }
     * 
     * @param request Login credentials (validated)
     * @return ResponseEntity with LoginResponse or error
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        logger.info("Login attempt for username: {}", request.getUsername());
        
        // Validate credentials
        if (!userService.validateCredentials(request.getUsername(), request.getPassword())) {
            logger.warn("Failed login attempt for username: {}", request.getUsername());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Unauthorized");
            errorResponse.put("message", "Invalid username or password");
            errorResponse.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
        
        // Get user details
        Optional<AuthUser> userOptional = userService.findByUsername(request.getUsername());
        if (userOptional.isEmpty()) {
            // Should not happen if validateCredentials passed, but handle defensive
            logger.error("User not found after successful validation: {}", request.getUsername());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        
        AuthUser user = userOptional.get();
        
        // Generate JWT token
        String token = jwtService.generateToken(user.getUserId(), user.getUsername(), user.getRole());
        
        // Build response
        UserInfo userInfo = UserInfo.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
        
        LoginResponse response = LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .user(userInfo)
                .build();
        
        logger.info("Login successful for user: {} (userId: {})", user.getUsername(), user.getUserId());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Health check endpoint for authentication service.
     * 
     * @return OK status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "authentication");
        return ResponseEntity.ok(response);
    }
}
