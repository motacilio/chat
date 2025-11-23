package com.chat.service;

import com.chat.model.AuthUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * User Service (POC - In-Memory)
 * 
 * Responsibility: Manages user lookup and credential validation for authentication.
 * Does NOT: Generate JWT tokens (see JwtService), manage user profiles (see User entity/repository).
 * 
 * Distributed Systems Concept: Separates authentication data access from token generation.
 * POC uses HashMap for fast iteration. Production replaces with UserRepository (MongoDB)
 * without changing JwtService or AuthenticationController.
 * 
 * POC Implementation: Hardcoded users (alice, bob, admin).
 * Production Migration: Replace with @Autowired UserRepository, add BCrypt password verification.
 * 
 * See: specs/001-ubiquitous-messaging-platform/authentication-jwt-data-model.md (Test Data section)
 */
@Service
public class UserService {
    
    /**
     * In-memory user store (POC only).
     * 
     * Production: Replace with:
     * @Autowired
     * private UserRepository userRepository;
     */
    private final Map<String, AuthUser> users = new HashMap<>();
    
    /**
     * Initializes hardcoded test users.
     * 
     * Users:
     * - alice (password123) - ROLE_USER - UUID: a1a1a1a1-1111-1111-1111-111111111111
     * - bob (password123) - ROLE_USER - UUID: b2b2b2b2-2222-2222-2222-222222222222
     * - admin (admin123) - ROLE_ADMIN - UUID: 00000000-0000-0000-0000-000000000000
     * 
     * Production: Remove constructor, load users from MongoDB.
     */
    public UserService() {
        // POC Test User 1: Alice
        users.put("alice", AuthUser.builder()
                .userId("a1a1a1a1-1111-1111-1111-111111111111")
                .username("alice")
                .password("password123")  // WARNING: Plain text (POC only)
                .role("ROLE_USER")
                .createdAt(Instant.now())
                .build());
        
        // POC Test User 2: Bob
        users.put("bob", AuthUser.builder()
                .userId("b2b2b2b2-2222-2222-2222-222222222222")
                .username("bob")
                .password("password123")  // WARNING: Plain text (POC only)
                .role("ROLE_USER")
                .createdAt(Instant.now())
                .build());
        
        // POC Test User 3: Admin
        users.put("admin", AuthUser.builder()
                .userId("00000000-0000-0000-0000-000000000000")
                .username("admin")
                .password("admin123")  // WARNING: Plain text (POC only)
                .role("ROLE_ADMIN")
                .createdAt(Instant.now())
                .build());
    }
    
    /**
     * Finds user by username.
     * 
     * POC Implementation: HashMap lookup.
     * 
     * Production Implementation:
     * return userRepository.findByUsername(username);
     * 
     * @param username Login username
     * @return Optional<AuthUser> containing user if found
     */
    public Optional<AuthUser> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }
    
    /**
     * Validates user credentials.
     * 
     * POC Implementation: Plain text password comparison.
     * 
     * Production Implementation:
     * return user != null && bCryptPasswordEncoder.matches(password, user.getPassword());
     * 
     * @param username Login username
     * @param password Plain text password
     * @return true if credentials valid
     */
    public boolean validateCredentials(String username, String password) {
        Optional<AuthUser> user = findByUsername(username);
        
        // POC: Plain text comparison (UNACCEPTABLE in production)
        return user.isPresent() && user.get().getPassword().equals(password);
    }
}
