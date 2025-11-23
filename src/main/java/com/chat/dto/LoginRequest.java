package com.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login Request DTO
 * 
 * Responsibility: Encapsulates login credentials from REST endpoint.
 * Does NOT: Validate business logic, handle authentication, store state.
 * 
 * Distributed Systems Concept: DTOs provide contract stability across service versions.
 * Validation constraints documented in API contract enable client-side validation,
 * reducing invalid requests and improving API responsiveness.
 * 
 * See: POST /api/auth/login in specs/001-ubiquitous-messaging-platform/authentication-jwt-data-model.md
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    
    /**
     * Username for authentication.
     * 
     * Validation Rules:
     * - Required (not blank)
     * - Length: 3-50 characters
     * - Pattern: Alphanumeric + underscore only
     */
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username must contain only letters, numbers, and underscores")
    private String username;
    
    /**
     * Password for authentication.
     * 
     * Validation Rules:
     * - Required (not blank)
     * - Minimum length: 8 characters
     * 
     * POC Note: Plain text comparison in UserService.
     * Production: BCrypt hash comparison.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
