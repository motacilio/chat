/**
 * Mock Webhook Sender - Simula envio de webhooks do WhatsApp e Telegram
 * 
 * Uso:
 *   node mock-webhook-sender.js --platform whatsapp --url https://seu-tunnel.trycloudflare.com
 *   node mock-webhook-sender.js --platform telegram --url https://seu-tunnel.trycloudflare.com
 *   node mock-webhook-sender.js --platform both --url https://seu-tunnel.trycloudflare.com
 */

const https = require('https');
const http = require('http');
const crypto = require('crypto');

// ============================================================================
// CONFIGURAÇÃO
// ============================================================================

const config = {
  whatsapp: {
    // Endpoint para novas mensagens RECEBIDAS (novo controller)
    incomingEndpoint: '/api/webhooks/whatsapp/incoming',
    // Endpoint para status updates de mensagens ENVIADAS (controller existente)
    statusEndpoint: '/api/webhooks/whatsapp',
    secret: 'whatsapp-test-secret-key-12345', // Deve coincidir com application.yml
    contentType: 'application/json',
  },
  telegram: {
    // Endpoint para novas mensagens RECEBIDAS (novo controller)
    incomingEndpoint: '/api/webhooks/telegram',
    // Endpoint para status updates (se necessário futuramente)
    statusEndpoint: '/api/webhooks/telegram/status',
    secret: 'telegram-test-secret-key-12345', // Deve coincidir com application.yml
    contentType: 'application/json',
  }
};

// ============================================================================
// PAYLOADS DE EXEMPLO
// ============================================================================

const whatsappPayloads = {
  // Nova mensagem de texto recebida
  textMessage: {
    object: 'whatsapp_business_account',
    entry: [{
      id: '123456789',
      changes: [{
        value: {
          messaging_product: 'whatsapp',
          metadata: {
            display_phone_number: '15551234567',
            phone_number_id: '987654321'
          },
          contacts: [{
            profile: { name: 'João Silva' },
            wa_id: '5511999887766'
          }],
          messages: [{
            from: '5511999887766',
            id: 'wamid.HBgNNTUxMTk5OTg4Nzc2NhUCABIYFjNBMkYwQzBDRjREMTdGNEJEOEI0AA==',
            timestamp: Math.floor(Date.now() / 1000).toString(),
            text: { body: 'Olá! Esta é uma mensagem de teste do WhatsApp.' },
            type: 'text'
          }]
        },
        field: 'messages'
      }]
    }]
  },

  // Status de mensagem entregue - FORMATO SIMPLIFICADO PARA TESTE
  messageDelivered: {
    platformMessageId: 'wamid.TEST' + Date.now(),
    messageId: '660e8400-e29b-41d4-a716-446655440000',
    status: 'DELIVERED',
    externalRecipientId: '5511999887766',
    timestamp: new Date().toISOString()
  },

  // Status de mensagem lida - FORMATO SIMPLIFICADO PARA TESTE
  messageRead: {
    platformMessageId: 'wamid.TEST' + Date.now(),
    messageId: '660e8400-e29b-41d4-a716-446655440000',
    status: 'READ',
    externalRecipientId: '5511999887766',
    timestamp: new Date().toISOString()
  }
};

const telegramPayloads = {
  // Nova mensagem de texto recebida
  textMessage: {
    update_id: 123456789,
    message: {
      message_id: 456,
      from: {
        id: 987654321,
        is_bot: false,
        first_name: 'João',
        last_name: 'Silva',
        username: 'joaosilva',
        language_code: 'pt-br'
      },
      chat: {
        id: 987654321,
        first_name: 'João',
        last_name: 'Silva',
        username: 'joaosilva',
        type: 'private'
      },
      date: Math.floor(Date.now() / 1000),
      text: 'Olá! Esta é uma mensagem de teste do Telegram.'
    }
  },

  // Mensagem com foto
  photoMessage: {
    update_id: 123456790,
    message: {
      message_id: 457,
      from: {
        id: 987654321,
        is_bot: false,
        first_name: 'João',
        last_name: 'Silva',
        username: 'joaosilva',
        language_code: 'pt-br'
      },
      chat: {
        id: 987654321,
        first_name: 'João',
        last_name: 'Silva',
        username: 'joaosilva',
        type: 'private'
      },
      date: Math.floor(Date.now() / 1000),
      photo: [{
        file_id: 'AgACAgEAAxkBAAIByGXxxx',
        file_unique_id: 'AQADyyyy',
        file_size: 12345,
        width: 800,
        height: 600
      }],
      caption: 'Olha essa foto!'
    }
  },

  // Callback query (botão pressionado)
  callbackQuery: {
    update_id: 123456791,
    callback_query: {
      id: '123456789012345678',
      from: {
        id: 987654321,
        is_bot: false,
        first_name: 'João',
        last_name: 'Silva',
        username: 'joaosilva',
        language_code: 'pt-br'
      },
      message: {
        message_id: 458,
        date: Math.floor(Date.now() / 1000),
        chat: {
          id: 987654321,
          type: 'private'
        }
      },
      chat_instance: '-123456789',
      data: 'button_clicked'
    }
  }
};

// ============================================================================
// FUNÇÕES DE ASSINATURA
// ============================================================================

/**
 * Gera assinatura HMAC SHA256 para WhatsApp
 */
function generateWhatsAppSignature(payload, secret) {
  const hmac = crypto.createHmac('sha256', secret);
  hmac.update(JSON.stringify(payload));
  return 'sha256=' + hmac.digest('hex');
}

/**
 * Gera hash SHA256 para Telegram (usado em secret_token)
 */
function generateTelegramHash(payload, secret) {
  const hash = crypto.createHash('sha256');
  hash.update(secret + JSON.stringify(payload));
  return hash.digest('hex');
}

// ============================================================================
// FUNÇÃO DE ENVIO DE WEBHOOK
// ============================================================================

/**
 * Envia webhook HTTP/HTTPS
 */
function sendWebhook(url, platform, payloadType, payload) {
  // Determinar endpoint baseado no tipo de payload
  let endpoint;
  if (platform === 'whatsapp') {
    // Status updates (delivered/read) → statusEndpoint
    // Mensagens novas (textMessage) → incomingEndpoint
    if (payloadType === 'messageDelivered' || payloadType === 'messageRead') {
      endpoint = config.whatsapp.statusEndpoint;
    } else {
      endpoint = config.whatsapp.incomingEndpoint;
    }
  } else if (platform === 'telegram') {
    // Sempre usar incomingEndpoint para Telegram por enquanto
    endpoint = config.telegram.incomingEndpoint;
  }

  const targetUrl = new URL(url + endpoint);
  const isHttps = targetUrl.protocol === 'https:';
  const client = isHttps ? https : http;

  const requestBody = JSON.stringify(payload);
  
  const options = {
    hostname: targetUrl.hostname,
    port: targetUrl.port || (isHttps ? 443 : 80),
    path: targetUrl.pathname,
    method: 'POST',
    headers: {
      'Content-Type': config[platform].contentType,
      'Content-Length': Buffer.byteLength(requestBody),
      'User-Agent': platform === 'whatsapp' ? 'WhatsApp/2.0' : 'TelegramBot/1.0'
    }
  };

  // Adicionar cabeçalhos de assinatura
  if (platform === 'whatsapp') {
    options.headers['X-Hub-Signature-256'] = generateWhatsAppSignature(payload, config.whatsapp.secret);
  } else if (platform === 'telegram') {
    // Telegram usa X-Telegram-Bot-Api-Secret-Token
    options.headers['X-Telegram-Bot-Api-Secret-Token'] = config.telegram.secret;
  }

  console.log(`\n${'='.repeat(80)}`);
  console.log(`📤 Enviando webhook ${platform.toUpperCase()} - ${payloadType}`);
  console.log(`${'='.repeat(80)}`);
  console.log(`URL: ${targetUrl.href}`);
  console.log(`Headers:`);
  Object.entries(options.headers).forEach(([key, value]) => {
    console.log(`  ${key}: ${value}`);
  });
  console.log(`\nPayload:`);
  console.log(JSON.stringify(payload, null, 2));

  return new Promise((resolve, reject) => {
    const req = client.request(options, (res) => {
      let responseData = '';

      res.on('data', (chunk) => {
        responseData += chunk;
      });

      res.on('end', () => {
        console.log(`\n✅ Resposta recebida - Status: ${res.statusCode}`);
        if (responseData) {
          console.log(`Corpo da resposta: ${responseData}`);
        }
        console.log(`${'='.repeat(80)}\n`);
        
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve({ status: res.statusCode, data: responseData });
        } else {
          reject(new Error(`HTTP ${res.statusCode}: ${responseData}`));
        }
      });
    });

    req.on('error', (error) => {
      console.error(`\n❌ Erro ao enviar webhook: ${error.message}`);
      console.log(`${'='.repeat(80)}\n`);
      reject(error);
    });

    req.write(requestBody);
    req.end();
  });
}

// ============================================================================
// FUNÇÃO PRINCIPAL
// ============================================================================

async function main() {
  const args = process.argv.slice(2);
  
  // Parse argumentos
  let targetUrl = null;
  let platform = 'both';
  let payloadType = 'textMessage';
  let delay = 2000; // Delay entre webhooks em ms

  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--url' && args[i + 1]) {
      targetUrl = args[i + 1];
      i++;
    } else if (args[i] === '--platform' && args[i + 1]) {
      platform = args[i + 1];
      i++;
    } else if (args[i] === '--type' && args[i + 1]) {
      payloadType = args[i + 1];
      i++;
    } else if (args[i] === '--delay' && args[i + 1]) {
      delay = parseInt(args[i + 1]);
      i++;
    }
  }

  // Validação
  if (!targetUrl) {
    console.error('❌ Erro: URL é obrigatória');
    console.log('\nUso:');
    console.log('  node mock-webhook-sender.js --url https://seu-tunnel.trycloudflare.com');
    console.log('\nOpções:');
    console.log('  --platform <whatsapp|telegram|both>  Plataforma (padrão: both)');
    console.log('  --type <textMessage|messageDelivered|messageRead|photoMessage|callbackQuery>');
    console.log('  --delay <ms>                         Delay entre webhooks (padrão: 2000ms)');
    console.log('\nExemplos:');
    console.log('  node mock-webhook-sender.js --url https://abc.trycloudflare.com --platform whatsapp');
    console.log('  node mock-webhook-sender.js --url https://abc.trycloudflare.com --platform telegram --type photoMessage');
    console.log('  node mock-webhook-sender.js --url http://localhost:8081');
    process.exit(1);
  }

  console.log('\n🚀 Mock Webhook Sender Iniciado');
  console.log(`Target URL: ${targetUrl}`);
  console.log(`Platform: ${platform}`);
  console.log(`Payload Type: ${payloadType}`);
  console.log(`Delay: ${delay}ms\n`);

  try {
    // Enviar webhooks conforme plataforma selecionada
    if (platform === 'whatsapp' || platform === 'both') {
      const payload = whatsappPayloads[payloadType] || whatsappPayloads.textMessage;
      await sendWebhook(targetUrl, 'whatsapp', payloadType, payload);
      
      if (platform === 'both') {
        await new Promise(resolve => setTimeout(resolve, delay));
      }
    }

    if (platform === 'telegram' || platform === 'both') {
      const payload = telegramPayloads[payloadType] || telegramPayloads.textMessage;
      await sendWebhook(targetUrl, 'telegram', payloadType, payload);
    }

    console.log('✅ Todos os webhooks enviados com sucesso!\n');
    
  } catch (error) {
    console.error(`❌ Erro: ${error.message}\n`);
    process.exit(1);
  }
}

// Executar
if (require.main === module) {
  main();
}

module.exports = { sendWebhook, whatsappPayloads, telegramPayloads };
