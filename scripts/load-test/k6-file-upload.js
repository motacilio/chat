/**
 * k6 Load Test - File Upload
 * 
 * Teste de carga para upload de arquivos via REST API
 * 
 * Cenários:
 * 1. Arquivos pequenos (< 1MB): 50 uploads/s
 * 2. Arquivos médios (1-10MB): 10 uploads/s
 * 3. Arquivos grandes (10-100MB): 2 uploads/s
 * 
 * Métricas coletadas:
 * - Throughput de upload (MB/s)
 * - Latência por tamanho de arquivo
 * - Taxa de erro
 * 
 * Uso:
 *   k6 run --out json=upload-results.json scripts/load-test/k6-file-upload.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { randomBytes } from 'k6/crypto';

// Métricas customizadas
const uploadErrors = new Rate('upload_errors');
const uploadLatency = new Trend('upload_latency_ms');
const uploadThroughput = new Trend('upload_throughput_mbps');
const filesUploaded = new Counter('files_uploaded');
const bytesUploaded = new Counter('bytes_uploaded');

// Configuração do teste
export const options = {
    scenarios: {
        // Cenário 1: Arquivos pequenos (< 1MB)
        small_files: {
            executor: 'constant-arrival-rate',
            rate: 50, // 50 uploads/segundo
            timeUnit: '1s',
            duration: '2m',
            preAllocatedVUs: 20,
            maxVUs: 50,
            tags: { file_size: 'small' },
        },
        
        // Cenário 2: Arquivos médios (1-10MB)
        medium_files: {
            executor: 'constant-arrival-rate',
            startTime: '2m30s',
            rate: 10, // 10 uploads/segundo
            timeUnit: '1s',
            duration: '3m',
            preAllocatedVUs: 10,
            maxVUs: 30,
            tags: { file_size: 'medium' },
        },
        
        // Cenário 3: Arquivos grandes (10-100MB)
        large_files: {
            executor: 'constant-arrival-rate',
            startTime: '6m',
            rate: 2, // 2 uploads/segundo
            timeUnit: '1s',
            duration: '4m',
            preAllocatedVUs: 5,
            maxVUs: 10,
            tags: { file_size: 'large' },
        },
    },
    
    // Thresholds
    thresholds: {
        'upload_latency_ms{file_size:small}': ['p(95)<1000'],    // Pequenos < 1s
        'upload_latency_ms{file_size:medium}': ['p(95)<5000'],   // Médios < 5s
        'upload_latency_ms{file_size:large}': ['p(95)<30000'],   // Grandes < 30s
        'upload_errors': ['rate<0.05'],                          // < 5% de erros
        'http_req_failed': ['rate<0.05'],
    },
};

// Base URL da API
const BASE_URL = __ENV.API_URL || 'http://localhost:8081';

// Tamanhos de arquivo por cenário (em bytes)
const FILE_SIZES = {
    small: [100 * 1024, 500 * 1024, 1024 * 1024],              // 100KB, 500KB, 1MB
    medium: [2 * 1024 * 1024, 5 * 1024 * 1024, 10 * 1024 * 1024], // 2MB, 5MB, 10MB
    large: [20 * 1024 * 1024, 50 * 1024 * 1024, 100 * 1024 * 1024], // 20MB, 50MB, 100MB
};

// Pool de usuários
const USERS = [
    { username: 'alice', password: 'password123' },
    { username: 'bob', password: 'password123' },
];

let userTokens = {};
let userIds = {};

/**
 * Setup: Autenticar usuários
 */
export function setup() {
    console.log('=== SETUP: Autenticando usuários para upload ===');
    
    USERS.forEach(user => {
        const loginRes = http.post(
            `${BASE_URL}/api/auth/login`,
            JSON.stringify({
                username: user.username,
                password: user.password
            }),
            {
                headers: { 'Content-Type': 'application/json' },
                timeout: '30s'
            }
        );
        
        if (loginRes.status === 200) {
            const body = JSON.parse(loginRes.body);
            userTokens[user.username] = body.token;
            userIds[user.username] = body.user_id;
            console.log(`✅ ${user.username} autenticado`);
        }
    });
    
    return { userTokens, userIds };
}

/**
 * Função principal: Upload de arquivo
 */
export default function(data) {
    const user = USERS[Math.floor(Math.random() * USERS.length)];
    const token = data.userTokens[user.username];
    const userId = data.userIds[user.username];
    
    if (!token) return;
    
    // Determinar tamanho do arquivo baseado no cenário
    const scenario = __ENV.SCENARIO || 'small';
    const fileSizeCategory = scenario.includes('small') ? 'small' 
                            : scenario.includes('medium') ? 'medium' 
                            : 'large';
    
    const sizeOptions = FILE_SIZES[fileSizeCategory];
    const fileSize = sizeOptions[Math.floor(Math.random() * sizeOptions.length)];
    
    // Gerar conteúdo do arquivo (bytes aleatórios)
    const fileContent = randomBytes(fileSize);
    const fileName = `test-file-${Date.now()}-${Math.random().toString(36).substring(7)}.bin`;
    
    // Preparar multipart form data
    const formData = {
        file: http.file(fileContent, fileName, 'application/octet-stream'),
        conversation_id: `conv-upload-${Math.floor(Math.random() * 100)}`,
        sender_id: userId,
    };
    
    // Upload do arquivo
    const uploadStart = Date.now();
    const uploadRes = http.post(
        `${BASE_URL}/api/files/upload`,
        formData,
        {
            headers: {
                'Authorization': `Bearer ${token}`
            },
            timeout: '60s',
            tags: { 
                name: 'file_upload',
                file_size_category: fileSizeCategory
            }
        }
    );
    const uploadDuration = Date.now() - uploadStart;
    
    // Calcular throughput (MB/s)
    const fileSizeMB = fileSize / (1024 * 1024);
    const uploadDurationSec = uploadDuration / 1000;
    const throughputMBps = uploadDurationSec > 0 ? fileSizeMB / uploadDurationSec : 0;
    
    // Validar resposta
    const uploadOk = check(uploadRes, {
        'file uploaded successfully': (r) => r.status === 201 || r.status === 200,
        'response has file_id': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.file_id !== undefined || body.message_id !== undefined;
            } catch {
                return false;
            }
        },
    });
    
    if (uploadOk) {
        filesUploaded.add(1);
        bytesUploaded.add(fileSize);
        uploadLatency.add(uploadDuration);
        uploadThroughput.add(throughputMBps);
        
        console.log(`✅ Upload OK: ${fileName} (${(fileSizeMB).toFixed(2)}MB, ${uploadDuration}ms, ${throughputMBps.toFixed(2)}MB/s)`);
    } else {
        uploadErrors.add(1);
        console.warn(`❌ Upload falhou: ${fileName} - Status ${uploadRes.status}`);
    }
    
    // Think time proporcional ao tamanho do arquivo
    const thinkTime = fileSizeCategory === 'small' ? 0.5 
                    : fileSizeCategory === 'medium' ? 2 
                    : 5;
    sleep(Math.random() * thinkTime + 0.5);
}

/**
 * Teardown: Resumo do teste
 */
export function teardown(data) {
    console.log('=== TEARDOWN: Teste de upload concluído ===');
}

/**
 * Summary customizado
 */
export function handleSummary(data) {
    const metrics = data.metrics;
    
    let summary = '\n=== K6 FILE UPLOAD SUMMARY ===\n\n';
    
    summary += `📁 Arquivos:\n`;
    summary += `   Total uploads: ${metrics.files_uploaded?.values?.count || 0}\n`;
    summary += `   Total bytes: ${((metrics.bytes_uploaded?.values?.count || 0) / (1024 * 1024 * 1024)).toFixed(2)}GB\n`;
    summary += `   Taxa de erro: ${((metrics.upload_errors?.values?.rate || 0) * 100).toFixed(2)}%\n\n`;
    
    summary += `⏱️ Latência:\n`;
    summary += `   Média: ${(metrics.upload_latency_ms?.values?.avg || 0).toFixed(2)}ms\n`;
    summary += `   p95: ${(metrics.upload_latency_ms?.values?.['p(95)'] || 0).toFixed(2)}ms\n`;
    summary += `   p99: ${(metrics.upload_latency_ms?.values?.['p(99)'] || 0).toFixed(2)}ms\n\n`;
    
    summary += `📊 Throughput:\n`;
    summary += `   Média: ${(metrics.upload_throughput_mbps?.values?.avg || 0).toFixed(2)}MB/s\n`;
    summary += `   Máximo: ${(metrics.upload_throughput_mbps?.values?.max || 0).toFixed(2)}MB/s\n\n`;
    
    return {
        'results/upload-summary.json': JSON.stringify(data, null, 2),
        'stdout': summary,
    };
}
