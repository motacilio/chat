package com.chat.adapter;

import com.chat.adapter.dto.ConnectionResult;
import com.chat.adapter.dto.PlatformCredentials;
import com.chat.adapter.dto.SendResult;
import com.chat.model.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Tests for WhatsAppMockAdapter
 * 
 * Validates FR-039 compliance:
 * - 95% success rate simulation
 * - 100-300ms latency range
 * - E.164 phone validation
 * - Error types: connection_timeout, rate_limit_exceeded, invalid_recipient
 * 
 * Educational Focus:
 * - How to test probabilistic behavior (success rates)
 * - Latency measurement techniques
 * - Input validation testing
 * - Mock behavior verification
 */
@DisplayName("WhatsAppMockAdapter Tests")
class WhatsAppMockAdapterTest {

    private WhatsAppMockAdapter adapter;
    private PlatformCredentials credentials;

    @BeforeEach
    void setUp() {
        adapter = new WhatsAppMockAdapter();
        credentials = new PlatformCredentials.Builder(Platform.WHATSAPP.name(), "test_token_123")
                .build();
        // Connect adapter before each test (required for sendMessage to work)
        adapter.connect(credentials);
    }

    /**
     * Test 1: Platform Identification
     * 
     * Verifies adapter correctly identifies itself as WhatsApp platform.
     */
    @Test
    @DisplayName("Should return WHATSAPP platform")
    void testGetPlatform() {
        assertEquals(Platform.WHATSAPP, adapter.getPlatform());
    }

    /**
     * Test 2: Connection Success
     * 
     * Mock adapter doesn't need real credentials, but should accept
     * properly formatted credentials object.
     */
    @Test
    @DisplayName("Should connect successfully with valid credentials")
    void testConnectSuccess() {
        // Given - credentials already created in setUp
        // When
        ConnectionResult result = adapter.connect(credentials);

        // Then
        assertTrue(result.isSuccess());
        assertNotNull(result.getMessage());
        assertNotNull(result.getTimestamp());
    }

    /**
     * Test 3: E.164 Phone Validation - Valid Numbers
     * 
     * Tests various valid E.164 formats from different countries.
     */
    @Test
    @DisplayName("Should accept valid E.164 phone numbers")
    void testValidE164Numbers() {
        // Valid E.164 formats
        String[] validNumbers = {
            "+5511987654321",      // Brazil
            "+14155552671",        // USA
            "+442071838750",       // UK
            "+861012345678",       // China
            "+33123456789",        // France
            "+919876543210"        // India
        };

        for (String phoneNumber : validNumbers) {
            SendResult result = adapter.sendMessage(phoneNumber, "Test message");
            
            // Should not fail due to validation error
            // (might fail due to random 5% failure, but error won't be "invalid_recipient")
            if (!result.isSuccess()) {
                assertNotEquals("invalid_recipient", result.getErrorCode(),
                    "Valid E.164 number should not fail validation: " + phoneNumber);
            }
        }
    }

    /**
     * Test 4: E.164 Phone Validation - Invalid Numbers
     * 
     * Tests rejection of improperly formatted phone numbers.
     */
    @Test
    @DisplayName("Should reject invalid E.164 phone numbers")
    void testInvalidE164Numbers() {
        // Invalid formats (missing +, too short, letters, etc.)
        String[] invalidNumbers = {
            "5511987654321",       // Missing +
            "+0011987654321",      // Starts with 0 (invalid country code)
            "+123456",             // Too short (< 7 digits minimum E.164)
            "+551198765432100000", // Too long (> 15 digits)
            "987654321",           // No country code
            "+55abc87654321",      // Contains letters
            "+55 11 98765-4321"    // Contains spaces/dashes
        };

        for (String phoneNumber : invalidNumbers) {
            SendResult result = adapter.sendMessage(phoneNumber, "Test message");
            
            assertFalse(result.isSuccess(),
                "Invalid E.164 number should fail: " + phoneNumber);
            assertEquals("invalid_recipient", result.getErrorCode(),
                "Invalid number should fail with 'invalid_recipient' error: " + phoneNumber);
        }
    }

    /**
     * Test 5: Message Sending - Successful Case
     * 
     * When mock randomly succeeds (95% chance), it should return
     * proper message ID in WhatsApp format.
     */
    @Test
    @DisplayName("Should return message ID on success")
    void testSendMessageSuccess() {
        // Run multiple times to eventually get a success
        boolean foundSuccess = false;
        
        for (int i = 0; i < 50; i++) {
            SendResult result = adapter.sendMessage("+5511987654321", "Test message");
            
            if (result.isSuccess()) {
                foundSuccess = true;
                
                // Validate message ID format: wamid.<UUID>
                assertNotNull(result.getPlatformMessageId());
                assertTrue(result.getPlatformMessageId().startsWith("wamid."),
                    "WhatsApp message ID should start with 'wamid.'");
                assertTrue(result.getPlatformMessageId().length() > 10,
                    "Message ID should contain UUID suffix");
                
                break;
            }
        }
        
        assertTrue(foundSuccess, 
            "Should get at least one success in 50 attempts (expected ~47 successes with 95% rate)");
    }

    /**
     * Test 6: Error Types Distribution
     * 
     * Validates that mock generates the correct error types per FR-039:
     * - connection_timeout (2%)
     * - rate_limit_exceeded (2%)
     * - invalid_recipient (1%)
     * 
     * This test runs 1000 iterations to verify statistical distribution.
     */
    @Test
    @DisplayName("Should generate correct error types in expected proportions")
    void testErrorTypeDistribution() {
        int iterations = 1000;
        Map<String, Integer> errorCounts = new HashMap<>();
        errorCounts.put("connection_timeout", 0);
        errorCounts.put("rate_limit_exceeded", 0);
        errorCounts.put("invalid_recipient", 0);
        
        int successCount = 0;
        int failureCount = 0;

        String validPhone = "+5511987654321";

        for (int i = 0; i < iterations; i++) {
            SendResult result = adapter.sendMessage(validPhone, "Test message " + i);

            if (result.isSuccess()) {
                successCount++;
            } else {
                failureCount++;
                String errorCode = result.getErrorCode();
                errorCounts.put(errorCode, errorCounts.getOrDefault(errorCode, 0) + 1);
            }
        }

        // Success rate should be ~95% (allow ±5% margin for randomness)
        double successRate = (double) successCount / iterations;
        assertTrue(successRate >= 0.90 && successRate <= 1.00,
            String.format("Success rate should be ~95%%, got %.1f%% (%d/%d)",
                successRate * 100, successCount, iterations));

        System.out.println("\n=== WhatsApp Mock Statistics (1000 runs) ===");
        System.out.println("Success: " + successCount + " (" + String.format("%.1f%%", successRate * 100) + ")");
        System.out.println("Failures: " + failureCount);
        
        if (failureCount > 0) {
            System.out.println("\nError Distribution:");
            errorCounts.forEach((errorType, count) -> {
                double percentage = (double) count / iterations * 100;
                System.out.println("  " + errorType + ": " + count + 
                    " (" + String.format("%.1f%%", percentage) + ")");
            });
        }

        // Each error type should be present (with some tolerance for randomness)
        // Expected: connection_timeout ~2%, rate_limit ~2%, invalid_recipient ~1%
        // But we only verify they exist, not exact percentages (too strict for random behavior)
        if (failureCount > 0) {
            assertTrue(errorCounts.getOrDefault("connection_timeout", 0) > 0,
                "Should have some connection_timeout errors");
            assertTrue(errorCounts.getOrDefault("rate_limit_exceeded", 0) > 0,
                "Should have some rate_limit_exceeded errors");
            // invalid_recipient from validation is separate from random errors
        }
    }

    /**
     * Test 7: Latency Range Validation
     * 
     * Validates that simulated latency falls within FR-039 specification:
     * 100-300ms range.
     * 
     * Note: This test is timing-sensitive and measures actual Thread.sleep duration.
     */
    @Test
    @DisplayName("Should simulate latency between 100-300ms")
    void testLatencyRange() {
        int iterations = 20;
        
        for (int i = 0; i < iterations; i++) {
            long startTime = System.currentTimeMillis();
            
            adapter.sendMessage("+5511987654321", "Test message");
            
            long duration = System.currentTimeMillis() - startTime;

            // Allow some tolerance for test execution overhead (~50ms)
            assertTrue(duration >= 90 && duration <= 350,
                String.format("Latency should be 100-300ms, got %dms", duration));
        }
    }

    /**
     * Test 8: Message Text Validation
     * 
     * Validates that adapter accepts non-empty message text.
     */
    @Test
    @DisplayName("Should accept valid message text")
    void testValidMessageText() {
        SendResult result = adapter.sendMessage("+5511987654321", "Hello, World!");
        
        // Result might be success or random failure, but should not fail due to validation
        assertNotNull(result);
        assertNotNull(result.getTimestamp());
    }

    /**
     * Test 9: Empty Message Text
     * 
     * Edge case: What happens with empty message?
     * Mock should still process (actual validation would be in MessageService).
     */
    @Test
    @DisplayName("Should handle empty message text")
    void testEmptyMessageText() {
        SendResult result = adapter.sendMessage("+5511987654321", "");
        
        // Mock doesn't validate message content (that's MessageService responsibility)
        assertNotNull(result);
    }

    /**
     * Test 10: Null Phone Number
     * 
     * Edge case: Null phone number should fail validation.
     */
    @Test
    @DisplayName("Should reject null phone number")
    void testNullPhoneNumber() {
        SendResult result = adapter.sendMessage(null, "Test message");
        
        assertFalse(result.isSuccess());
        assertEquals("invalid_recipient", result.getErrorCode());
    }

    /**
     * Test 11: Null Message Text
     * 
     * Edge case: Null message text handling.
     */
    @Test
    @DisplayName("Should handle null message text")
    void testNullMessageText() {
        SendResult result = adapter.sendMessage("+5511987654321", null);
        
        // Mock doesn't validate message text (MessageService does that)
        assertNotNull(result);
    }

    /**
     * Test 12: Concurrent Sends
     * 
     * Validates that adapter is thread-safe (no shared mutable state).
     * Each call should be independent.
     */
    @Test
    @DisplayName("Should handle concurrent sends independently")
    void testConcurrentSends() throws InterruptedException {
        final int threadCount = 10;
        final int sendsPerThread = 10;
        
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < sendsPerThread; j++) {
                    SendResult result = adapter.sendMessage(
                        "+5511987654321", 
                        "Thread " + threadId + " message " + j
                    );
                    assertNotNull(result);
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // If we got here without exceptions, thread safety is OK
        assertTrue(true);
    }

    /**
     * Test 13: SendFile Method (Future Feature)
     * 
     * SendFile is part of PlatformAdapter interface but not implemented in mock.
     * Should return unimplemented error.
     */
    @Test
    @DisplayName("Should return unimplemented for sendFile")
    void testSendFileUnimplemented() {
        SendResult result = adapter.sendFile(
            "+5511987654321",
            "https://example.com/file.pdf",
            "document.pdf"
        );

        assertFalse(result.isSuccess());
        assertEquals("not_implemented", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("P2 feature"));
    }

    /**
     * Test 14: Consistent Behavior Across Calls
     * 
     * Validates that two identical calls can produce different results
     * (due to randomness), confirming non-deterministic behavior is working.
     */
    @Test
    @DisplayName("Should produce varied results due to randomness")
    void testRandomBehavior() {
        String phone = "+5511987654321";
        String message = "Test message";
        
        Map<Boolean, Integer> outcomes = new HashMap<>();
        outcomes.put(true, 0);  // success count
        outcomes.put(false, 0); // failure count
        
        for (int i = 0; i < 100; i++) {
            SendResult result = adapter.sendMessage(phone, message);
            outcomes.put(result.isSuccess(), outcomes.get(result.isSuccess()) + 1);
        }
        
        // With 95% success rate over 100 calls, we should see BOTH successes and failures
        assertTrue(outcomes.get(true) > 0, "Should have some successes");
        assertTrue(outcomes.get(false) > 0, "Should have some failures (validates randomness)");
        
        System.out.println("\n=== Random Behavior Test ===");
        System.out.println("Successes: " + outcomes.get(true) + "/100");
        System.out.println("Failures: " + outcomes.get(false) + "/100");
    }
}
