package com.chat.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UuidValidator
 */
class UuidValidatorTest {

    @Test
    void testValidUuid() {
        // Given
        String validUuid = "550e8400-e29b-41d4-a716-446655440000";

        // When & Then
        assertTrue(UuidValidator.isValid(validUuid));
        assertDoesNotThrow(() -> UuidValidator.validateOrThrow(validUuid, "test_field"));
    }

    @Test
    void testInvalidUuid() {
        // Given
        String invalidUuid = "not-a-valid-uuid";

        // When & Then
        assertFalse(UuidValidator.isValid(invalidUuid));
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> UuidValidator.validateOrThrow(invalidUuid, "test_field")
        );
        
        assertTrue(exception.getMessage().contains("test_field"));
        assertTrue(exception.getMessage().contains("valid UUID"));
    }

    @Test
    void testNullUuid() {
        // When & Then
        assertFalse(UuidValidator.isValid(null));
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> UuidValidator.validateOrThrow(null, "user_id")
        );
        
        assertTrue(exception.getMessage().contains("user_id"));
    }

    @Test
    void testEmptyUuid() {
        // When & Then
        assertFalse(UuidValidator.isValid(""));
        assertFalse(UuidValidator.isValid("   "));
    }

    @Test
    void testGenerateUuid() {
        // When
        String uuid1 = UuidValidator.generate();
        String uuid2 = UuidValidator.generate();

        // Then
        assertNotNull(uuid1);
        assertNotNull(uuid2);
        assertTrue(UuidValidator.isValid(uuid1));
        assertTrue(UuidValidator.isValid(uuid2));
        assertNotEquals(uuid1, uuid2);
    }

    @Test
    void testUppercaseUuid() {
        // Given
        String uppercaseUuid = "550E8400-E29B-41D4-A716-446655440000";

        // When & Then
        assertTrue(UuidValidator.isValid(uppercaseUuid));
    }
}
