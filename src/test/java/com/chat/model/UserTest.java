package com.chat.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for User entity
 */
class UserTest {

    @Test
    void testCreateUser() {
        // Given
        String userId = "550e8400-e29b-41d4-a716-446655440000";
        String username = "john_doe";
        String email = "john@example.com";

        // When
        User user = User.create(userId, username, email);

        // Then
        assertNotNull(user);
        assertEquals(userId, user.getUserId());
        assertEquals(username, user.getUsername());
        assertEquals(email, user.getEmail());
        assertNotNull(user.getCreatedAt());
    }

    @Test
    void testBuilderPattern() {
        // Given & When
        User user = User.builder()
                .userId("test-user-id")
                .username("test_user")
                .email("test@example.com")
                .build();

        // Then
        assertNotNull(user);
        assertEquals("test-user-id", user.getUserId());
        assertEquals("test_user", user.getUsername());
        assertEquals("test@example.com", user.getEmail());
    }
}
