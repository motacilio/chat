/**
 * k6 Performance Benchmark - 10,000 Concurrent Users
 * 
 * Target: NFR-003 - System must handle 10,000 concurrent users with p95 latency <100ms
 * 
 * Test Methodology:
 * 1. Ramp-up: 0 → 10,000 VUs over 5 minutes
 * 2. Sustained Load: 10,000 VUs for 10 minutes
 * 3. Ramp-down: 10,000 → 0 VUs over 2 minutes
 * 
 * Metrics Measured:
 * - Throughput: requests/second
 * - Latency: p50, p95, p99 (target p95 <100ms)
 * - Error Rate: % of failed requests (target <1%)
 * - Concurrent Users: active VUs
 * 
 * Prerequisites:
 * - Chat API running on localhost:8081
 * - gRPC server running on localhost:9090
 * - MongoDB, Kafka, MinIO healthy
 * - Prometheus/Grafana running for monitoring
 * 
 * Usage:
 *   k6 run --out json=results/benchmark-10k.json scripts/load-test/k6-benchmark-10k-users.js
 *   k6 run --out influxdb=http://localhost:8086/k6 scripts/load-test/k6-benchmark-10k-users.js
 * 
 * WARNING: This test generates HEAVY load. Ensure infrastructure is production-ready.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';
import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.1/index.js";

// Custom Metrics
const errorRate = new Rate('errors');
const healthCheckLatency = new Trend('health_check_latency_ms');
const actuatorLatency = new Trend('actuator_latency_ms');
const prometheusLatency = new Trend('prometheus_latency_ms');
const successfulRequests = new Counter('successful_requests');
const failedRequests = new Counter('failed_requests');
const activeUsers = new Gauge('active_users');

// Test Configuration
export const options = {
    scenarios: {
        benchmark_10k: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                // Ramp-up: 0 → 10,000 users over 5 minutes
                { duration: '5m', target: 10000 },
                
                // Sustained load: 10,000 users for 10 minutes
                { duration: '10m', target: 10000 },
                
                // Ramp-down: 10,000 → 0 users over 2 minutes
                { duration: '2m', target: 0 },
            ],
            gracefulRampDown: '30s',
        },
    },
    
    // Thresholds (Pass/Fail Criteria)
    thresholds: {
        // Latency: p95 must be <100ms (NFR-003)
        'health_check_latency_ms': [
            'p(95)<100',  // 95th percentile <100ms
            'p(99)<200',  // 99th percentile <200ms
        ],
        'actuator_latency_ms': [
            'p(95)<150',  // Actuator endpoints can be slightly slower
            'p(99)<300',
        ],
        'prometheus_latency_ms': [
            'p(95)<200',  // Prometheus metrics can be heavier
            'p(99)<500',
        ],
        
        // Error Rate: <1% (NFR target)
        'errors': ['rate<0.01'],
        
        // HTTP Duration: Overall response time
        'http_req_duration': [
            'p(95)<100',
            'p(99)<200',
        ],
        
        // HTTP Failure Rate
        'http_req_failed': ['rate<0.01'],
    },
    
    // External Metrics Output
    summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// Base URLs
const BASE_URL = __ENV.API_URL || 'http://localhost:8081';
const ACTUATOR_URL = `${BASE_URL}/actuator`;

/**
 * Main test function executed by each VU
 */
export default function () {
    // Track active VUs
    activeUsers.add(1);
    
    // Test 1: Health Check (lightweight endpoint)
    const healthResponse = http.get(`${ACTUATOR_URL}/health`, {
        tags: { name: 'health_check' },
    });
    
    const healthCheck = check(healthResponse, {
        'health status is 200': (r) => r.status === 200,
        'health response time <100ms': (r) => r.timings.duration < 100,
        'health status is UP': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.status === 'UP';
            } catch (e) {
                return false;
            }
        },
    });
    
    if (healthCheck) {
        successfulRequests.add(1);
        healthCheckLatency.add(healthResponse.timings.duration);
    } else {
        failedRequests.add(1);
        errorRate.add(1);
    }
    
    sleep(0.5); // 500ms think time
    
    // Test 2: Actuator Info (medium weight)
    const infoResponse = http.get(`${ACTUATOR_URL}/info`, {
        tags: { name: 'actuator_info' },
    });
    
    const infoCheck = check(infoResponse, {
        'info status is 200': (r) => r.status === 200,
        'info response time <150ms': (r) => r.timings.duration < 150,
    });
    
    if (infoCheck) {
        successfulRequests.add(1);
        actuatorLatency.add(infoResponse.timings.duration);
    } else {
        failedRequests.add(1);
        errorRate.add(1);
    }
    
    sleep(0.5);
    
    // Test 3: Prometheus Metrics (heavy endpoint - tests real load)
    const prometheusResponse = http.get(`${ACTUATOR_URL}/prometheus`, {
        tags: { name: 'prometheus_metrics' },
    });
    
    const prometheusCheck = check(prometheusResponse, {
        'prometheus status is 200': (r) => r.status === 200,
        'prometheus response time <200ms': (r) => r.timings.duration < 200,
        'prometheus has metrics': (r) => r.body && r.body.length > 1000,
    });
    
    if (prometheusCheck) {
        successfulRequests.add(1);
        prometheusLatency.add(prometheusResponse.timings.duration);
    } else {
        failedRequests.add(1);
        errorRate.add(1);
    }
    
    // Simulate realistic user behavior (1-3 second think time)
    sleep(1 + Math.random() * 2);
    
    activeUsers.add(-1);
}

/**
 * Setup function - runs once before test starts
 */
export function setup() {
    console.log('🚀 Starting 10,000 Concurrent Users Benchmark');
    console.log(`📊 Target: p95 latency <100ms, error rate <1%`);
    console.log(`🔗 API URL: ${BASE_URL}`);
    console.log(`⏱️ Duration: 17 minutes (5m ramp-up + 10m sustained + 2m ramp-down)`);
    
    // Verify API is accessible
    const healthCheck = http.get(`${ACTUATOR_URL}/health`);
    if (healthCheck.status !== 200) {
        throw new Error(`API health check failed: ${healthCheck.status}`);
    }
    
    console.log('✅ API is healthy, starting benchmark...\n');
    
    return { startTime: new Date().toISOString() };
}

/**
 * Teardown function - runs once after test completes
 */
export function teardown(data) {
    console.log(`\n✅ Benchmark completed`);
    console.log(`Started: ${data.startTime}`);
    console.log(`Ended: ${new Date().toISOString()}`);
}

/**
 * Custom summary report
 */
export function handleSummary(data) {
    const passed = data.metrics.errors.values.rate < 0.01 && 
                   data.metrics.http_req_duration.values['p(95)'] < 100;
    
    console.log('\n' + '='.repeat(80));
    console.log('📊 PERFORMANCE BENCHMARK RESULTS - 10,000 Concurrent Users');
    console.log('='.repeat(80));
    console.log(`\n🎯 TARGET: p95 latency <100ms, error rate <1%`);
    console.log(`📈 RESULT: ${passed ? '✅ PASSED' : '❌ FAILED'}\n`);
    
    console.log('Latency (p95):');
    console.log(`  Health Check: ${data.metrics.health_check_latency_ms.values['p(95)'].toFixed(2)}ms`);
    console.log(`  Actuator:     ${data.metrics.actuator_latency_ms.values['p(95)'].toFixed(2)}ms`);
    console.log(`  Prometheus:   ${data.metrics.prometheus_latency_ms.values['p(95)'].toFixed(2)}ms`);
    console.log(`  Overall:      ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms`);
    
    console.log(`\nError Rate: ${(data.metrics.errors.values.rate * 100).toFixed(2)}%`);
    console.log(`Successful Requests: ${data.metrics.successful_requests.values.count}`);
    console.log(`Failed Requests: ${data.metrics.failed_requests.values.count}`);
    console.log(`Throughput: ${data.metrics.http_reqs.values.rate.toFixed(2)} req/s`);
    
    console.log('\n' + '='.repeat(80) + '\n');
    
    return {
        'results/benchmark-10k-summary.txt': textSummary(data, { indent: ' ', enableColors: true }),
        'results/benchmark-10k-report.html': htmlReport(data),
        'results/benchmark-10k-results.json': JSON.stringify(data, null, 2),
    };
}
