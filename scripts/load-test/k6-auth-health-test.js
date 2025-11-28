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
 *   k6 run --out json=results.json scripts/load-test/k6-auth-health-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

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
    { username: 'alice', password: 'password123' },
    { username: 'bob', password: 'password123' },
];

/**
 * Setup: Executado uma vez antes do teste
 */
export function setup() {
    console.log('=== SETUP: Validando conectividade ===');
    
    // Verificar se API está respondendo
    const healthRes = http.get(`${BASE_URL}/actuator/health`);
    if (healthRes.status !== 200) {
        console.error(`❌ API não está respondendo: ${healthRes.status}`);
        return { apiAvailable: false };
    }
    
    console.log('✅ API disponível');
    
    // Testar login de um usuário
    const testLoginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({
            username: USERS[0].username,
            password: USERS[0].password
        }),
        {
            headers: { 'Content-Type': 'application/json' }
        }
    );
    
    if (testLoginRes.status === 200) {
        console.log('✅ Autenticação funcionando');
        return { apiAvailable: true };
    } else {
        console.error(`❌ Autenticação falhou: ${testLoginRes.status}`);
        return { apiAvailable: false };
    }
}

/**
 * Função principal: Executada por cada VU em cada iteração
 */
export default function(data) {
    if (!data.apiAvailable) {
        console.error('API não disponível, abortando teste');
        return;
    }
    
    // Seleciona usuário aleatório do pool
    const user = USERS[Math.floor(Math.random() * USERS.length)];
    
    // 1. Fazer login (simula re-autenticação periódica)
    const authStart = Date.now();
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
    const authDuration = Date.now() - authStart;
    
    const loginOk = check(loginRes, {
        'login successful': (r) => r.status === 200,
        'has token': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.token !== undefined;
            } catch {
                return false;
            }
        },
        'auth latency < 200ms': () => authDuration < 200,
    });
    
    if (loginOk) {
        loginsSuccessful.add(1);
        authLatency.add(authDuration);
    } else {
        loginsFailed.add(1);
        errorRate.add(1);
    }
    
    // 2. Health check (30% das iterações)
    if (Math.random() < 0.3) {
        const healthStart = Date.now();
        const healthRes = http.get(
            `${BASE_URL}/actuator/health`,
            {
                tags: { name: 'health_check' }
            }
        );
        const healthDuration = Date.now() - healthStart;
        
        const healthOk = check(healthRes, {
            'health check ok': (r) => r.status === 200,
            'status is UP': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    return body.status === 'UP';
                } catch {
                    return false;
                }
            },
        });
        
        if (healthOk) {
            healthCheckLatency.add(healthDuration);
        } else {
            errorRate.add(1);
        }
    }
    
    // Think time: Pausa entre requisições (0.5-2.5 segundos)
    sleep(Math.random() * 2 + 0.5);
}

/**
 * Teardown: Executado uma vez após o teste
 */
export function teardown(data) {
    console.log('=== TEARDOWN: Teste concluído ===');
}

/**
 * Summary personalizado
 */
export function handleSummary(data) {
    const authSuccessCount = data.metrics.logins_successful ? data.metrics.logins_successful.values.count : 0;
    const authFailCount = data.metrics.logins_failed ? data.metrics.logins_failed.values.count : 0;
    const errorRateValue = data.metrics.errors ? data.metrics.errors.values.rate : 0;
    const authLatencyP95 = data.metrics.auth_latency ? data.metrics.auth_latency.values['p(95)'] : 0;
    const healthLatencyP95 = data.metrics.health_check_latency ? data.metrics.health_check_latency.values['p(95)'] : 0;
    
    console.log('\n=== K6 LOAD TEST SUMMARY ===');
    console.log('\n📊 Autenticação:');
    console.log(`   Logins bem-sucedidos: ${authSuccessCount}`);
    console.log(`   Logins falhados: ${authFailCount}`);
    console.log(`   Taxa de erro: ${(errorRateValue * 100).toFixed(2)}%`);
    console.log(`   Latência p95: ${authLatencyP95.toFixed(2)}ms`);
    
    console.log('\n💚 Health Check:');
    console.log(`   Latência p95: ${healthLatencyP95.toFixed(2)}ms`);
    
    console.log('\n✅ Thresholds:');
    for (const [threshold, result] of Object.entries(data.metrics)) {
        if (result.thresholds) {
            for (const [name, value] of Object.entries(result.thresholds)) {
                const status = value.ok ? '✅' : '❌';
                console.log(`   ${status} ${name}`);
            }
        }
    }
    
    return {
        'stdout': '', // Já imprimimos acima
        'results/auth-health-summary.json': JSON.stringify(data, null, 2),
        'results/auth-health-summary.txt': `
K6 Load Test Summary
====================

Autenticação:
- Logins bem-sucedidos: ${authSuccessCount}
- Logins falhados: ${authFailCount}
- Taxa de erro: ${(errorRateValue * 100).toFixed(2)}%
- Latência p95: ${authLatencyP95.toFixed(2)}ms

Health Check:
- Latência p95: ${healthLatencyP95.toFixed(2)}ms

Para testar mensagens gRPC, use: ghz --insecure --proto src/main/proto/chat_service.proto --call chat_api.v1.ChatService/SendMessage -d '{"conversation_id":"test","sender_id":"alice","message_text":"test"}' localhost:9090
        `.trim(),
    };
}
