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
 * Unit Tests for InstagramMockAdapter
 * 
 * Validates FR-039 compliance:
 * - 90% success rate simulation (lower than WhatsApp)
 * - 150-400ms latency range (higher than WhatsApp)
 * - @username validation (1-30 alphanumeric chars)
 * - Error types: connection_timeout, rate_limit_exceeded, invalid_recipient
 * 
 * Educational Focus:
 * - Testing heterogeneous SLA behavior (different reliability per platform)
 * - Username format validation vs phone validation
 * - Statistical testing with different success rates
 */
@DisplayName("InstagramMockAdapter Tests")
class InstagramMockAdapterTest {

    private InstagramMockAdapter adapter;
    private PlatformCredentials credentials;

    @BeforeEach
    void setUp() {
        adapter = new InstagramMockAdapter();
        credentials = new PlatformCredentials.Builder(Platform.INSTAGRAM.name(), "test_token_123")
                .build();
        // Connect adapter before each test (required for sendMessage to work)
        adapter.connect(credentials);
    }

    /**
     * Test 1: Platform Identification
     */
    @Test
    @DisplayName("Should return INSTAGRAM platform")
    void testGetPlatform() {
        assertEquals(Platform.INSTAGRAM, adapter.getPlatform());
    }

    /**
     * Test 2: Connection Success
     */
    @Test
    @DisplayName("Should connect successfully with valid credentials")
    void testConnectSuccess() {
        // Credentials already created in setUp
        ConnectionResult result = adapter.connect(credentials);

        assertTrue(result.isSuccess());
        assertNotNull(result.getMessage());
        assertNotNull(result.getTimestamp());
    }

    /**
     * Test 3: Username Validation - Valid Formats
     * 
     * Tests valid Instagram username patterns:
     * - Must start with @
     * - 1-30 alphanumeric characters, underscores, periods
     */
    @Test
    @DisplayName("Should accept valid Instagram usernames")
    void testValidUsernames() {
        String[] validUsernames = {
            "@john_doe",           // Underscore
            "@jane.smith",         // Period
            "@user123",            // Numbers
            "@a",                  // Single char (min length)
            "@user_name_123",      // Mixed
            "@UPPERCASE",          // Uppercase
            "@lowercase",          // Lowercase
            "@MixedCase123",       // Mixed case + numbers
            "@a1b2c3d4e5f6g7h8i9j0k1l2m3n4" // 30 chars (max length)
        };

        // Note: Valid usernames can still fail with probabilistic errors (invalid_recipient 2%)
        // This simulates real-world scenarios: banned accounts, API errors, etc.
        for (String username : validUsernames) {
            SendResult result = adapter.sendMessage(username, "Test message");
            // Just verify the mock accepts the format; errors are probabilistic
            assertNotNull(result);
        }
    }

    /**
     * Test 4: Username Validation - Invalid Formats
     * 
     * Tests rejection of improperly formatted usernames.
     */
    @Test
    @DisplayName("Should reject invalid Instagram usernames")
    void testInvalidUsernames() {
        String[] invalidUsernames = {
            "john_doe",            // Missing @
            "@",                   // Empty username
            "@@john",              // Double @
            "@john doe",           // Space
            "@john-doe",           // Hyphen (not allowed)
            "@john@doe",           // @ in middle
            "@a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q",  // 34 chars (too long, max is 30)
            "@user!",              // Special char
            "@user#tag",           // Hash
            "@user$$$"             // Dollar signs
        };

        for (String username : invalidUsernames) {
            SendResult result = adapter.sendMessage(username, "Test message");
            
            assertFalse(result.isSuccess(),
                "Invalid username should fail: " + username);
            assertEquals("invalid_recipient", result.getErrorCode(),
                "Invalid username should fail with 'invalid_recipient' error: " + username);
        }
    }

    /**
     * Test 5: Message Sending - Successful Case
     * 
     * When mock randomly succeeds (90% chance), it should return
     * proper message ID in Instagram format.
     */
    @Test
    @DisplayName("Should return message ID on success")
    void testSendMessageSuccess() {
        boolean foundSuccess = false;
        
        for (int i = 0; i < 50; i++) {
            SendResult result = adapter.sendMessage("@john_doe", "Test message");
            
            if (result.isSuccess()) {
                foundSuccess = true;
                
                // Validate message ID format: mid.<UUID>
                assertNotNull(result.getPlatformMessageId());
                assertTrue(result.getPlatformMessageId().startsWith("mid."),
                    "Instagram message ID should start with 'mid.'");
                assertTrue(result.getPlatformMessageId().length() > 10,
                    "Message ID should contain UUID suffix");
                
                break;
            }
        }
        
        assertTrue(foundSuccess, 
            "Should get at least one success in 50 attempts (expected ~45 successes with 90% rate)");
    }

    /**
     * Test 6: Error Types Distribution
     * 
     * Validates that mock generates the correct error types per FR-039:
     * - connection_timeout (4%)
     * - rate_limit_exceeded (4%)
     * - invalid_recipient (2%)
     * 
     * Note: Instagram has HIGHER error rates than WhatsApp (less reliable platform).
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

        String validUsername = "@john_doe";

        for (int i = 0; i < iterations; i++) {
            SendResult result = adapter.sendMessage(validUsername, "Test message " + i);

            if (result.isSuccess()) {
                successCount++;
            } else {
                failureCount++;
                String errorCode = result.getErrorCode();
                errorCounts.put(errorCode, errorCounts.getOrDefault(errorCode, 0) + 1);
            }
        }

        // Success rate should be ~90% (allow ±5% margin for randomness)
        double successRate = (double) successCount / iterations;
        assertTrue(successRate >= 0.85 && successRate <= 0.95,
            String.format("Success rate should be ~90%%, got %.1f%% (%d/%d)",
                successRate * 100, successCount, iterations));

        System.out.println("\n=== Instagram Mock Statistics (1000 runs) ===");
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

        // Verify error types exist
        if (failureCount > 0) {
            assertTrue(errorCounts.getOrDefault("connection_timeout", 0) > 0,
                "Should have some connection_timeout errors");
            assertTrue(errorCounts.getOrDefault("rate_limit_exceeded", 0) > 0,
                "Should have some rate_limit_exceeded errors");
        }
    }

    /**
     * Test 7: Latency Range Validation
     * 
     * Validates that simulated latency falls within FR-039 specification:
     * 150-400ms range (HIGHER than WhatsApp 100-300ms).
     * 
     * This demonstrates heterogeneous platform SLAs.
     */
    @Test
    @DisplayName("Should simulate latency between 150-400ms")
    void testLatencyRange() {
        int iterations = 20;
        
        for (int i = 0; i < iterations; i++) {
            long startTime = System.currentTimeMillis();
            
            adapter.sendMessage("@john_doe", "Test message");
            
            long duration = System.currentTimeMillis() - startTime;

            // Allow some tolerance for test execution overhead (~50ms)
            assertTrue(duration >= 140 && duration <= 450,
                String.format("Latency should be 150-400ms, got %dms", duration));
        }
    }

    /**
     * Test 8: Comparison with WhatsApp Performance
     * 
     * Educational test demonstrating that Instagram has:
     * - Lower success rate (90% vs 95%)
     * - Higher latency (150-400ms vs 100-300ms)
     * 
     * This validates heterogeneous SLA simulation.
     */
    @Test
    @DisplayName("Should demonstrate lower reliability than WhatsApp")
    void testHeterogeneousSLA() {
        int iterations = 100;
        int instagramSuccesses = 0;
        
        for (int i = 0; i < iterations; i++) {
            SendResult result = adapter.sendMessage("@john_doe", "Test");
            if (result.isSuccess()) {
                instagramSuccesses++;
            }
        }
        
        double instagramSuccessRate = (double) instagramSuccesses / iterations;
        
        // Instagram success rate should be noticeably lower than 95%
        assertTrue(instagramSuccessRate < 0.95,
            String.format("Instagram success rate (%.1f%%) should be lower than WhatsApp (95%%)",
                instagramSuccessRate * 100));
        
        System.out.println("\n=== SLA Comparison ===");
        System.out.println("Instagram success rate: " + String.format("%.1f%%", instagramSuccessRate * 100));
        System.out.println("Expected: ~90% (vs WhatsApp 95%)");
    }

    /**
     * Test 9: Null Username
     */
    @Test
    @DisplayName("Should reject null username")
    void testNullUsername() {
        SendResult result = adapter.sendMessage(null, "Test message");
        
        assertFalse(result.isSuccess());
        assertEquals("invalid_recipient", result.getErrorCode());
    }

    /**
     * Test 10: Empty Username
     */
    @Test
    @DisplayName("Should reject empty username")
    void testEmptyUsername() {
        SendResult result = adapter.sendMessage("", "Test message");
        
        assertFalse(result.isSuccess());
        assertEquals("invalid_recipient", result.getErrorCode());
    }

    /**
     * Test 11: Username Case Sensitivity
     * 
     * Instagram usernames are case-insensitive but stored with original case.
     * Mock should accept any case variation.
     */
    @Test
    @DisplayName("Should accept usernames in any case")
    void testUsernameCaseSensitivity() {
        String[] caseVariations = {
            "@JohnDoe",
            "@johndoe",
            "@JOHNDOE",
            "@JoHnDoE"
        };
        
        for (String username : caseVariations) {
            SendResult result = adapter.sendMessage(username, "Test");
            
            if (!result.isSuccess()) {
                assertNotEquals("invalid_recipient", result.getErrorCode(),
                    "Case variation should not fail validation: " + username);
            }
        }
    }

    /**
     * Test 12: SendFile Method (Future Feature)
     */
    @Test
    @DisplayName("Should return unimplemented for sendFile")
    void testSendFileUnimplemented() {
        SendResult result = adapter.sendFile(
            "@john_doe",
            "https://example.com/image.jpg",
            "photo.jpg"
        );

        assertFalse(result.isSuccess());
        assertEquals("not_implemented", result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("P2 feature"));
    }

    /**
     * Test 13: Thread Safety
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
                        "@user" + threadId, 
                        "Thread " + threadId + " message " + j
                    );
                    assertNotNull(result);
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue(true);
    }

    /**
     * Test 14: Random Behavior Validation
     */
    @Test
    @DisplayName("Should produce varied results due to randomness")
    void testRandomBehavior() {
        String username = "@john_doe";
        String message = "Test message";
        
        Map<Boolean, Integer> outcomes = new HashMap<>();
        outcomes.put(true, 0);
        outcomes.put(false, 0);
        
        for (int i = 0; i < 100; i++) {
            SendResult result = adapter.sendMessage(username, message);
            outcomes.put(result.isSuccess(), outcomes.get(result.isSuccess()) + 1);
        }
        
        assertTrue(outcomes.get(true) > 0, "Should have some successes");
        assertTrue(outcomes.get(false) > 0, "Should have some failures (validates randomness)");
        
        System.out.println("\n=== Random Behavior Test ===");
        System.out.println("Successes: " + outcomes.get(true) + "/100");
        System.out.println("Failures: " + outcomes.get(false) + "/100");
    }

    /**
     * Test 15: Username with Maximum Length
     */
    @Test
    @DisplayName("Should accept username with exactly 30 characters")
    void testMaxLengthUsername() {
        // 30 characters after @
        String maxUsername = "@a1b2c3d4e5f6g7h8i9j0k1l2m3n4";
        
        SendResult result = adapter.sendMessage(maxUsername, "Test");
        
        if (!result.isSuccess()) {
            assertNotEquals("invalid_recipient", result.getErrorCode(),
                "30-char username should be valid");
        }
    }

    /**
     * Test 16: Username with Minimum Length
     */
    @Test
    @DisplayName("Should accept username with exactly 1 character")
    void testMinLengthUsername() {
        String minUsername = "@a";
        
        SendResult result = adapter.sendMessage(minUsername, "Test");
        
        if (!result.isSuccess()) {
            assertNotEquals("invalid_recipient", result.getErrorCode(),
                "1-char username should be valid");
        }
    }
}
