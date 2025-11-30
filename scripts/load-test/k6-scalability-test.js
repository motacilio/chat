# Teste de Escalabilidade - 1000+ Usuários Concorrentes
# Valida throughput, latência p95 e consumer lag com carga pesada
#
# Pré-requisitos:
# 1. Sistema rodando com docker-compose up
# 2. MongoDB replica set inicializado
# 3. Kafka tópicos criados
#
# Execução:
# k6 run scripts/load-test/k6-scalability-test.js

import grpc from 'k6/net/grpc';
import { check, sleep } from 'k6';
import { Counter, Trend, Gauge } from 'k6/metrics';

// Custom metrics
const sendMessageLatency = new Trend('send_message_latency_ms');
const successfulMessages = new Counter('successful_messages');
const failedMessages = new Counter('failed_messages');
const activeConnections = new Gauge('active_grpc_connections');

// gRPC client
const client = new grpc.Client();
client.load(['../../src/main/proto'], 'chat_service.proto', 'common_types.proto');

// Test configuration
export const options = {
  stages: [
    // Ramp-up: 0 → 500 users in 2 minutes (gradual load increase)
    { duration: '2m', target: 500 },
    
    // Sustain: 500 users for 3 minutes (steady state)
    { duration: '3m', target: 500 },
    
    // Spike: 500 → 1000 users in 1 minute (stress test)
    { duration: '1m', target: 1000 },
    
    // Sustain spike: 1000 users for 5 minutes (peak load)
    { duration: '5m', target: 1000 },
    
    // Ramp-down: 1000 → 0 users in 2 minutes (graceful shutdown)
    { duration: '2m', target: 0 },
  ],
  
  // SLO thresholds (Service Level Objectives)
  thresholds: {
    // Latency: p95 must be < 100ms (NFR-012)
    'send_message_latency_ms': ['p(95)<100', 'p(99)<200'],
    
    // Success rate: > 99% (NFR-007: 99.9% uptime → ~99% success in tests)
    'grpc_req_failed{method="SendMessage"}': ['rate<0.01'],
    
    // Throughput: system must handle 1000 msg/s (1000 users × 1 msg/s)
    'successful_messages': ['count>60000'],  // 60k messages in 13 minutes
  },
};

// Setup: Create JWT token for authentication (mock)
export function setup() {
  // In production, call authentication service
  // For this test, we'll use mock user IDs
  return {
    userCount: 1000,
    conversationCount: 100,  // 100 conversations shared by users
  };
}

// Main test scenario
export default function(data) {
  // Each virtual user represents a unique user
  const userId = `user-${__VU}`;  // VU = Virtual User ID (1-1000)
  const conversationId = `conv-${(__VU % data.conversationCount) + 1}`;  // Distribute across 100 conversations
  
  // Connect to gRPC server
  client.connect('localhost:9090', {
    plaintext: true,  // For development; use TLS in production
  });
  
  activeConnections.add(1);
  
  try {
    // Send message via gRPC
    const startTime = Date.now();
    
    const response = client.invoke('chat_api.v1.ChatService/SendMessage', {
      conversation_id: conversationId,
      message_text: `Load test message from ${userId} at ${new Date().toISOString()}`,
    });
    
    const latency = Date.now() - startTime;
    sendMessageLatency.add(latency);
    
    // Validate response
    const success = check(response, {
      'status is OK': (r) => r && r.status === grpc.StatusOK,
      'message_id returned': (r) => r && r.message && r.message.message_id,
      'latency < 500ms': () => latency < 500,  // Hard limit
    });
    
    if (success) {
      successfulMessages.add(1);
    } else {
      failedMessages.add(1);
      console.error(`SendMessage failed for ${userId} in ${conversationId}: ${response.error}`);
    }
    
  } catch (error) {
    failedMessages.add(1);
    console.error(`Exception in SendMessage for ${userId}: ${error}`);
  } finally {
    client.close();
    activeConnections.add(-1);
  }
  
  // Think time: 1 second between messages (realistic user behavior)
  sleep(1);
}

// Teardown: Print summary
export function teardown(data) {
  console.log('\n=== Scalability Test Summary ===');
  console.log(`Total users: ${data.userCount}`);
  console.log(`Total conversations: ${data.conversationCount}`);
  console.log('Check Grafana dashboard for detailed metrics:');
  console.log('  - http://localhost:3000 (Prometheus metrics)');
  console.log('  - Kafka Consumer Lag (should be < 1000 messages)');
  console.log('  - MongoDB Connection Pool (should be < 50)');
}
