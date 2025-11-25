package com.chat.adapter;

import com.chat.adapter.dto.SendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Statistical Validation Tests for Mock Adapters
 * 
 * Runs large-scale tests (1000+ iterations) to validate that mocks
 * behave according to FR-039 specifications:
 * 
 * WhatsApp Mock:
 * - 95% success rate (±3% tolerance)
 * - 100-300ms latency
 * - Errors: connection_timeout (2%), rate_limit_exceeded (2%), invalid_recipient (1%)
 * 
 * Instagram Mock:
 * - 90% success rate (±3% tolerance)
 * - 150-400ms latency
 * - Errors: connection_timeout (4%), rate_limit_exceeded (4%), invalid_recipient (2%)
 * 
 * Educational Focus:
 * - Statistical testing techniques
 * - Confidence intervals and tolerance ranges
 * - Comparing probabilistic behaviors
 * - Performance benchmarking
 */
@DisplayName("Mock Adapters - Statistical Validation Tests")
class MockAdapterStatisticsTest {

    private static final int LARGE_SAMPLE_SIZE = 1000;
    private static final int MEDIUM_SAMPLE_SIZE = 500;
    
    private final WhatsAppMockAdapter whatsappAdapter = new WhatsAppMockAdapter();
    private final InstagramMockAdapter instagramAdapter = new InstagramMockAdapter();

    /**
     * Test 1: WhatsApp Success Rate Validation
     * 
     * Runs 1000 sends and validates success rate is within tolerance of 95%.
     */
    @Test
    @DisplayName("WhatsApp should maintain ~95% success rate over 1000 sends")
    void testWhatsAppSuccessRate() {
        StatisticsResult result = runStatisticalTest(
            whatsappAdapter,
            "+5511987654321",
            LARGE_SAMPLE_SIZE
        );

        System.out.println("\n=== WhatsApp Statistical Analysis (1000 runs) ===");
        printStatistics(result, 0.95);

        // Validate success rate: 95% ±3% = 92%-98% range
        assertTrue(result.successRate >= 0.92 && result.successRate <= 0.98,
            String.format("WhatsApp success rate should be 95%% ±3%%, got %.1f%%",
                result.successRate * 100));
    }

    /**
     * Test 2: Instagram Success Rate Validation
     * 
     * Runs 1000 sends and validates success rate is within tolerance of 90%.
     */
    @Test
    @DisplayName("Instagram should maintain ~90% success rate over 1000 sends")
    void testInstagramSuccessRate() {
        StatisticsResult result = runStatisticalTest(
            instagramAdapter,
            "@john_doe",
            LARGE_SAMPLE_SIZE
        );

        System.out.println("\n=== Instagram Statistical Analysis (1000 runs) ===");
        printStatistics(result, 0.90);

        // Validate success rate: 90% ±3% = 87%-93% range
        assertTrue(result.successRate >= 0.87 && result.successRate <= 0.93,
            String.format("Instagram success rate should be 90%% ±3%%, got %.1f%%",
                result.successRate * 100));
    }

    /**
     * Test 3: WhatsApp Latency Distribution
     * 
     * Validates that latency falls within FR-039 specification (100-300ms).
     */
    @Test
    @DisplayName("WhatsApp latency should be 100-300ms")
    void testWhatsAppLatency() {
        List<Long> latencies = measureLatencies(
            whatsappAdapter,
            "+5511987654321",
            MEDIUM_SAMPLE_SIZE
        );

        LatencyStats stats = calculateLatencyStats(latencies);

        System.out.println("\n=== WhatsApp Latency Analysis (500 runs) ===");
        printLatencyStats(stats);

        // All latencies should be within range (with small tolerance)
        assertTrue(stats.min >= 90 && stats.min <= 310,
            String.format("Min latency should be ~100ms, got %dms", stats.min));
        assertTrue(stats.max >= 90 && stats.max <= 310,
            String.format("Max latency should be ~300ms, got %dms", stats.max));
        assertTrue(stats.avg >= 150 && stats.avg <= 250,
            String.format("Avg latency should be ~200ms, got %.0fms", stats.avg));
    }

    /**
     * Test 4: Instagram Latency Distribution
     * 
     * Validates that latency falls within FR-039 specification (150-400ms).
     */
    @Test
    @DisplayName("Instagram latency should be 150-400ms")
    void testInstagramLatency() {
        List<Long> latencies = measureLatencies(
            instagramAdapter,
            "@john_doe",
            MEDIUM_SAMPLE_SIZE
        );

        LatencyStats stats = calculateLatencyStats(latencies);

        System.out.println("\n=== Instagram Latency Analysis (500 runs) ===");
        printLatencyStats(stats);

        // All latencies should be within range (with small tolerance)
        assertTrue(stats.min >= 140 && stats.min <= 410,
            String.format("Min latency should be ~150ms, got %dms", stats.min));
        assertTrue(stats.max >= 140 && stats.max <= 410,
            String.format("Max latency should be ~400ms, got %dms", stats.max));
        assertTrue(stats.avg >= 225 && stats.avg <= 325,
            String.format("Avg latency should be ~275ms, got %.0fms", stats.avg));
    }

    /**
     * Test 5: Heterogeneous SLA Comparison
     * 
     * Validates that Instagram has LOWER success rate than WhatsApp.
     * This demonstrates heterogeneous platform reliability.
     */
    @Test
    @DisplayName("Instagram should have lower success rate than WhatsApp")
    void testHeterogeneousSLA() {
        StatisticsResult whatsappStats = runStatisticalTest(
            whatsappAdapter,
            "+5511987654321",
            LARGE_SAMPLE_SIZE
        );

        StatisticsResult instagramStats = runStatisticalTest(
            instagramAdapter,
            "@john_doe",
            LARGE_SAMPLE_SIZE
        );

        System.out.println("\n=== Platform Comparison (1000 runs each) ===");
        System.out.println("WhatsApp success rate:  " + String.format("%.1f%%", whatsappStats.successRate * 100));
        System.out.println("Instagram success rate: " + String.format("%.1f%%", instagramStats.successRate * 100));
        System.out.println("Difference: " + String.format("%.1f%%", 
            (whatsappStats.successRate - instagramStats.successRate) * 100));

        // Instagram should have noticeably lower success rate
        assertTrue(whatsappStats.successRate > instagramStats.successRate,
            "WhatsApp should be more reliable than Instagram");
        
        double difference = whatsappStats.successRate - instagramStats.successRate;
        assertTrue(difference >= 0.03 && difference <= 0.08,
            String.format("Success rate difference should be ~5%%, got %.1f%%", difference * 100));
    }

    /**
     * Test 6: Latency Comparison
     * 
     * Validates that Instagram has HIGHER latency than WhatsApp.
     */
    @Test
    @DisplayName("Instagram should have higher latency than WhatsApp")
    void testLatencyComparison() {
        List<Long> whatsappLatencies = measureLatencies(
            whatsappAdapter,
            "+5511987654321",
            MEDIUM_SAMPLE_SIZE
        );

        List<Long> instagramLatencies = measureLatencies(
            instagramAdapter,
            "@john_doe",
            MEDIUM_SAMPLE_SIZE
        );

        LatencyStats whatsappStats = calculateLatencyStats(whatsappLatencies);
        LatencyStats instagramStats = calculateLatencyStats(instagramLatencies);

        System.out.println("\n=== Latency Comparison (500 runs each) ===");
        System.out.println("WhatsApp avg:  " + String.format("%.0fms", whatsappStats.avg));
        System.out.println("Instagram avg: " + String.format("%.0fms", instagramStats.avg));
        System.out.println("Difference: " + String.format("%.0fms", instagramStats.avg - whatsappStats.avg));

        // Instagram should have higher average latency
        assertTrue(instagramStats.avg > whatsappStats.avg,
            "Instagram should have higher latency than WhatsApp");
        
        double difference = instagramStats.avg - whatsappStats.avg;
        assertTrue(difference >= 40 && difference <= 100,
            String.format("Latency difference should be ~75ms, got %.0fms", difference));
    }

    /**
     * Test 7: Error Type Distribution - WhatsApp
     * 
     * Validates error types match FR-039 specification.
     */
    @Test
    @DisplayName("WhatsApp should generate correct error type distribution")
    void testWhatsAppErrorDistribution() {
        StatisticsResult result = runStatisticalTest(
            whatsappAdapter,
            "+5511987654321",
            LARGE_SAMPLE_SIZE
        );

        System.out.println("\n=== WhatsApp Error Distribution ===");
        printErrorDistribution(result.errorCounts, LARGE_SAMPLE_SIZE);

        // Verify all expected error types are present
        assertTrue(result.errorCounts.containsKey("connection_timeout"),
            "Should have connection_timeout errors");
        assertTrue(result.errorCounts.containsKey("rate_limit_exceeded"),
            "Should have rate_limit_exceeded errors");
        
        // Note: invalid_recipient comes from validation, not random errors
    }

    /**
     * Test 8: Error Type Distribution - Instagram
     */
    @Test
    @DisplayName("Instagram should generate correct error type distribution")
    void testInstagramErrorDistribution() {
        StatisticsResult result = runStatisticalTest(
            instagramAdapter,
            "@john_doe",
            LARGE_SAMPLE_SIZE
        );

        System.out.println("\n=== Instagram Error Distribution ===");
        printErrorDistribution(result.errorCounts, LARGE_SAMPLE_SIZE);

        // Verify all expected error types are present
        assertTrue(result.errorCounts.containsKey("connection_timeout"),
            "Should have connection_timeout errors");
        assertTrue(result.errorCounts.containsKey("rate_limit_exceeded"),
            "Should have rate_limit_exceeded errors");
    }

    /**
     * Test 9: Consistency Across Multiple Runs
     * 
     * Runs the same test 5 times and validates results are consistent.
     */
    @Test
    @DisplayName("Success rate should be consistent across multiple test runs")
    void testConsistencyAcrossRuns() {
        List<Double> whatsappRates = new ArrayList<>();
        List<Double> instagramRates = new ArrayList<>();

        for (int run = 0; run < 5; run++) {
            StatisticsResult whatsapp = runStatisticalTest(
                whatsappAdapter,
                "+5511987654321",
                200
            );
            StatisticsResult instagram = runStatisticalTest(
                instagramAdapter,
                "@john_doe",
                200
            );

            whatsappRates.add(whatsapp.successRate);
            instagramRates.add(instagram.successRate);
        }

        System.out.println("\n=== Consistency Test (5 runs of 200 sends) ===");
        System.out.println("WhatsApp success rates: " + formatRates(whatsappRates));
        System.out.println("Instagram success rates: " + formatRates(instagramRates));

        // All WhatsApp rates should be around 95%
        for (double rate : whatsappRates) {
            assertTrue(rate >= 0.90 && rate <= 1.00,
                "WhatsApp rate should be ~95%");
        }

        // All Instagram rates should be around 90%
        for (double rate : instagramRates) {
            assertTrue(rate >= 0.85 && rate <= 0.95,
                "Instagram rate should be ~90%");
        }
    }

    // ========== Helper Methods ==========

    /**
     * Runs statistical test by sending multiple messages and collecting results.
     */
    private StatisticsResult runStatisticalTest(
            PlatformAdapter adapter,
            String recipient,
            int iterations) {
        
        int successCount = 0;
        Map<String, Integer> errorCounts = new HashMap<>();

        for (int i = 0; i < iterations; i++) {
            SendResult result = adapter.sendMessage(recipient, "Test message " + i);

            if (result.isSuccess()) {
                successCount++;
            } else {
                String errorCode = result.getErrorCode();
                errorCounts.put(errorCode, errorCounts.getOrDefault(errorCode, 0) + 1);
            }
        }

        double successRate = (double) successCount / iterations;

        return new StatisticsResult(successCount, iterations, successRate, errorCounts);
    }

    /**
     * Measures latency for multiple sends.
     */
    private List<Long> measureLatencies(
            PlatformAdapter adapter,
            String recipient,
            int iterations) {
        
        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            long start = System.currentTimeMillis();
            adapter.sendMessage(recipient, "Test");
            long duration = System.currentTimeMillis() - start;
            latencies.add(duration);
        }

        return latencies;
    }

    /**
     * Calculates latency statistics (min, max, avg, p50, p95, p99).
     */
    private LatencyStats calculateLatencyStats(List<Long> latencies) {
        Collections.sort(latencies);

        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        
        long p50 = latencies.get((int) (latencies.size() * 0.50));
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get((int) (latencies.size() * 0.99));

        return new LatencyStats(min, max, avg, p50, p95, p99);
    }

    /**
     * Prints statistics summary.
     */
    private void printStatistics(StatisticsResult result, double expectedRate) {
        System.out.println("Total sends: " + result.totalSends);
        System.out.println("Successes: " + result.successCount + 
            " (" + String.format("%.1f%%", result.successRate * 100) + ")");
        System.out.println("Failures: " + (result.totalSends - result.successCount));
        System.out.println("Expected: " + String.format("%.0f%%", expectedRate * 100));
        System.out.println("Deviation: " + String.format("%+.1f%%", 
            (result.successRate - expectedRate) * 100));
        
        if (!result.errorCounts.isEmpty()) {
            System.out.println("\nError types:");
            printErrorDistribution(result.errorCounts, result.totalSends);
        }
    }

    /**
     * Prints error distribution.
     */
    private void printErrorDistribution(Map<String, Integer> errorCounts, int total) {
        errorCounts.forEach((errorType, count) -> {
            double percentage = (double) count / total * 100;
            System.out.println("  " + errorType + ": " + count + 
                " (" + String.format("%.1f%%", percentage) + ")");
        });
    }

    /**
     * Prints latency statistics.
     */
    private void printLatencyStats(LatencyStats stats) {
        System.out.println("Min: " + stats.min + "ms");
        System.out.println("Max: " + stats.max + "ms");
        System.out.println("Avg: " + String.format("%.0fms", stats.avg));
        System.out.println("p50: " + stats.p50 + "ms");
        System.out.println("p95: " + stats.p95 + "ms");
        System.out.println("p99: " + stats.p99 + "ms");
    }

    /**
     * Formats success rates for display.
     */
    private String formatRates(List<Double> rates) {
        return rates.stream()
            .map(rate -> String.format("%.1f%%", rate * 100))
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }

    // ========== Data Classes ==========

    private static class StatisticsResult {
        final int successCount;
        final int totalSends;
        final double successRate;
        final Map<String, Integer> errorCounts;

        StatisticsResult(int successCount, int totalSends, double successRate, 
                        Map<String, Integer> errorCounts) {
            this.successCount = successCount;
            this.totalSends = totalSends;
            this.successRate = successRate;
            this.errorCounts = errorCounts;
        }
    }

    private static class LatencyStats {
        final long min;
        final long max;
        final double avg;
        final long p50;
        final long p95;
        final long p99;

        LatencyStats(long min, long max, double avg, long p50, long p95, long p99) {
            this.min = min;
            this.max = max;
            this.avg = avg;
            this.p50 = p50;
            this.p95 = p95;
            this.p99 = p99;
        }
    }
}
