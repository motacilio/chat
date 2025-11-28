/**
 * k6 Load Test - Authentication & Health Endpoints
 * 
 * NOTA: Este projeto usa gRPC para envio de mensagens, não REST API.
 * Este script testa apenas endpoints REST disponíveis (autenticação, health).
 * Para testes de carga de mensagens gRPC, use ferramentas como ghz (https://ghz.sh).
 * 
 * Cenários:
 * 1. Warmup: 1 → 100 VUs (7 min)
 * 2. Load test: 500 → 1000 VUs (18 min)
 * 3. Spike test: 2000 VUs (2 min)
 * 
 * Métricas coletadas:
 * - Throughput de autenticação (logins/segundo)
 * - Latência de endpoints (p50, p95, p99)
 * - Taxa de erro
 * 
 * Uso:
 *   k6 run --out json=results.json scripts/load-test/k6-send-messages.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// Métricas customizadas
const errorRate = new Rate('errors');
const authLatency = new Trend('auth_latency');
const healthCheckLatency = new Trend('health_check_latency');
const loginsSuccessful = new Counter('logins_successful');
const loginsFailed = new Counter('logins_failed');

// Configuração do teste
export const options = {
    // Cenário 1: Teste gradual (warmup)
    scenarios: {
        warmup: {
            executor: 'ramping-vus',
            startVUs: 1,
            stages: [
                { duration: '2m', target: 100 },   // ramp-up to 100 users
                { duration: '3m', target: 100 },   // stay at 100
                { duration: '2m', target: 0 },     // ramp-down
            ],
            gracefulRampDown: '30s',
            tags: { test_type: 'warmup' },
        },
        
        // Cenário 2: Carga sustentada
        load_test: {
            executor: 'ramping-vus',
            startTime: '7m', // Começa após warmup
            startVUs: 0,
            stages: [
                { duration: '3m', target: 500 },   // ramp-up to 500
                { duration: '5m', target: 500 },   // stay at 500
                { duration: '3m', target: 1000 },  // ramp-up to 1000
                { duration: '5m', target: 1000 },  // stay at 1000
                { duration: '2m', target: 0 },     // ramp-down
            ],
            gracefulRampDown: '30s',
            tags: { test_type: 'load' },
        },
        
        // Cenário 3: Spike test
        spike_test: {
            executor: 'ramping-vus',
            startTime: '25m', // Começa após load_test
            startVUs: 0,
            stages: [
                { duration: '30s', target: 2000 },  // spike to 2000
                { duration: '1m', target: 2000 },   // stay at 2000
                { duration: '30s', target: 0 },     // ramp-down
            ],
            gracefulRampDown: '10s',
            tags: { test_type: 'spike' },
        },
    },
    
    // Thresholds (critérios de aceitação)
    thresholds: {
        'http_req_duration': ['p(95)<100'],               // 95% das requisições < 100ms
        'http_req_duration{test_type:load}': ['p(99)<200'], // 99% < 200ms em load
        'errors': ['rate<0.01'],                          // Taxa de erro < 1%
        'http_req_failed': ['rate<0.01'],                 // Falhas HTTP < 1%
        'auth_latency': ['p(95)<150'],                    // Latência de auth < 150ms
        'health_check_latency': ['p(95)<50'],             // Health check < 50ms
    },
};

// Base URL da API
const BASE_URL = __ENV.API_URL || 'http://localhost:8081';

// Pool de usuários (simula múltiplos usuários autenticados)
const USERS = [
    { username: 'alice', password: 'password123', userId: 'a1a1a1a1-1111-1111-1111-111111111111' },
    { username: 'bob', password: 'password123', userId: 'b2b2b2b2-2222-2222-2222-222222222222' },
];

// Estado compartilhado
let userTokens = {};
let userIds = {};

/**
 * Setup: Executado uma vez antes do teste
 * Autentica todos os usuários do pool
 */
export function setup() {
    console.log('=== SETUP: Autenticando usuários ===');
    
    USERS.forEach(user => {
        const loginRes = http.post(
            `${BASE_URL}/api/auth/login`,
            JSON.stringify({
                username: user.username,
                password: user.password
            }),
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'auth_login' }
            }
        );
        
        if (loginRes.status === 200) {
            const body = JSON.parse(loginRes.body);
            userTokens[user.username] = body.token;
            // Usar userId do próprio objeto USERS se não vier no response
            userIds[user.username] = body.user_id || user.userId;
            console.log(`✅ ${user.username} autenticado (ID: ${userIds[user.username]})`);
        } else {
            console.error(`❌ Falha ao autenticar ${user.username}: ${loginRes.status}`);
        }
    });
    
    return { userTokens, userIds };
}

/**
 * Função principal: Executada por cada VU em cada iteração
 */
export default function(data) {
    // Seleciona usuário aleatório do pool
    const user = USERS[Math.floor(Math.random() * USERS.length)];
    const token = data.userTokens[user.username];
    const userId = data.userIds[user.username];
    
    if (!token || !userId) {
        console.error(`Token não encontrado para ${user.username}`);
        return;
    }
    
    // 1. Criar conversa (10% das iterações criam nova conversa)
    let conversationId;
    if (Math.random() < 0.1) {
        conversationId = createConversation(token, userId, data.userIds);
    } else {
        // Reutiliza ID de conversa existente (simulação)
        conversationId = `conv-${Math.floor(Math.random() * 100)}`;
    }
    
    // 2. Enviar mensagem
    const messageId = `msg-${Date.now()}-${randomString(8)}`;
    const messageText = `Load test message from ${user.username} at ${new Date().toISOString()}`;
    
    const sendStart = Date.now();
    const sendRes = http.post(
        `${BASE_URL}/api/messages`,
        JSON.stringify({
            message_id: messageId,
            conversation_id: conversationId,
            sender_id: userId,
            message_text: messageText
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            tags: { name: 'send_message' }
        }
    );
    const sendLatency = Date.now() - sendStart;
    
    // Validar resposta
    const sendOk = check(sendRes, {
        'message sent successfully': (r) => r.status === 201 || r.status === 200,
        'response has message_id': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.message_id !== undefined;
            } catch {
                return false;
            }
        },
        'latency < 200ms': (r) => sendLatency < 200,
    });
    
    if (sendOk) {
        messagesCreated.add(1);
        messageLatency.add(sendLatency);
    } else {
        errorRate.add(1);
        console.warn(`❌ Erro ao enviar mensagem: Status ${sendRes.status}`);
    }
    
    // 3. Testar idempotência (5% das iterações reenviam a mesma mensagem)
    if (Math.random() < 0.05) {
        const idempotentRes = http.post(
            `${BASE_URL}/api/messages`,
            JSON.stringify({
                message_id: messageId, // Mesmo message_id
                conversation_id: conversationId,
                sender_id: userId,
                message_text: messageText
            }),
            {
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${token}`
                },
                tags: { name: 'idempotent_check' }
            }
        );
        
        check(idempotentRes, {
            'idempotent request accepted': (r) => r.status === 200 || r.status === 201,
        });
        
        idempotentRequests.add(1);
    }
    
    // 4. Consultar status da mensagem (30% das iterações)
    if (Math.random() < 0.3) {
        const statusRes = http.get(
            `${BASE_URL}/api/messages/${messageId}/status`,
            {
                headers: { 'Authorization': `Bearer ${token}` },
                tags: { name: 'get_status' }
            }
        );
        
        check(statusRes, {
            'status retrieved': (r) => r.status === 200,
        });
    }
    
    // Think time: Pausa entre iterações (simula comportamento real)
    sleep(Math.random() * 2 + 0.5); // 0.5-2.5 segundos
}

/**
 * Helper: Criar conversa
 */
function createConversation(token, senderId, userIds) {
    // Seleciona destinatário aleatório
    const recipientId = Object.values(userIds)[Math.floor(Math.random() * Object.keys(userIds).length)];
    
    const convRes = http.post(
        `${BASE_URL}/api/conversations`,
        JSON.stringify({
            type: 'PRIVATE',
            participant_ids: [recipientId]
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            tags: { name: 'create_conversation' }
        }
    );
    
    if (convRes.status === 201 || convRes.status === 200) {
        const body = JSON.parse(convRes.body);
        return body.conversation_id;
    }
    
    // Fallback: Retorna ID aleatório
    return `conv-fallback-${Math.floor(Math.random() * 1000)}`;
}

/**
 * Teardown: Executado uma vez após o teste
 * Gera resumo dos resultados
 */
export function teardown(data) {
    console.log('=== TEARDOWN: Teste concluído ===');
    console.log(`Total de usuários autenticados: ${Object.keys(data.userTokens).length}`);
}

/**
 * Opções de saída customizadas
 * Exporta métricas detalhadas para análise posterior
 */
export function handleSummary(data) {
    return {
        'results/summary.json': JSON.stringify(data, null, 2),
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    };
}

// Helper para summary textual
function textSummary(data, options) {
    let summary = '\n=== K6 LOAD TEST SUMMARY ===\n\n';
    
    const metrics = data.metrics;
    
    summary += `📊 Requisições HTTP:\n`;
    summary += `   Total: ${metrics.http_reqs?.values?.count || 0}\n`;
    summary += `   Falhas: ${metrics.http_req_failed?.values?.rate || 0}%\n`;
    summary += `   Duração p95: ${(metrics.http_req_duration?.values?.['p(95)'] || 0).toFixed(2)}ms\n`;
    summary += `   Duração p99: ${(metrics.http_req_duration?.values?.['p(99)'] || 0).toFixed(2)}ms\n\n`;
    
    summary += `📨 Mensagens:\n`;
    summary += `   Criadas: ${metrics.messages_created?.values?.count || 0}\n`;
    summary += `   Taxa de erro: ${(metrics.errors?.values?.rate || 0) * 100}%\n`;
    summary += `   Latência média: ${(metrics.message_latency?.values?.avg || 0).toFixed(2)}ms\n`;
    summary += `   Latência p95: ${(metrics.message_latency?.values?.['p(95)'] || 0).toFixed(2)}ms\n\n`;
    
    summary += `🔄 Idempotência:\n`;
    summary += `   Requisições duplicadas: ${metrics.idempotent_requests?.values?.count || 0}\n\n`;
    
    summary += `✅ Thresholds:\n`;
    for (const [name, threshold] of Object.entries(data.thresholds || {})) {
        const status = threshold.ok ? '✅' : '❌';
        summary += `   ${status} ${name}\n`;
    }
    
    return summary;
}
