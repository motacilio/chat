/**
 * WebSocket Test Client - Tests real-time message streaming via WebSocket/STOMP.
 * 
 * Prerequisites:
 * 1. npm install sockjs-client stompjs
 * 2. Chat server running: java -jar target/meu-projeto-chat-1.0.0-SNAPSHOT.jar
 * 3. JWT token from login (e.g., via REST API)
 * 
 * Usage:
 * node scripts/setup/test-websocket-client.js <JWT_TOKEN>
 * 
 * Example:
 * node scripts/setup/test-websocket-client.js eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
 */

const SockJS = require('sockjs-client');
const Stomp = require('stompjs');

// Configuration
const WEBSOCKET_URL = 'http://localhost:8081/ws';
const JWT_TOKEN = process.argv[2];

if (!JWT_TOKEN) {
    console.error('ERROR: JWT token required');
    console.error('Usage: node test-websocket-client.js <JWT_TOKEN>');
    console.error('\nTo get JWT token:');
    console.error('1. Login via REST API: POST http://localhost:8081/api/auth/login');
    console.error('2. Copy the "token" field from response');
    process.exit(1);
}

// Extract userId from JWT (decode base64 payload)
function extractUserIdFromJWT(token) {
    try {
        const parts = token.split('.');
        if (parts.length !== 3) {
            throw new Error('Invalid JWT format');
        }
        
        const payload = JSON.parse(Buffer.from(parts[1], 'base64').toString('utf-8'));
        return payload.sub || payload.user_id || payload.userId;
    } catch (e) {
        console.error('Failed to decode JWT:', e.message);
        process.exit(1);
    }
}

const userId = extractUserIdFromJWT(JWT_TOKEN);
console.log(`[INFO] Extracted userId from JWT: ${userId}`);

// Create WebSocket connection
console.log(`[INFO] Connecting to WebSocket: ${WEBSOCKET_URL}`);
const socket = new SockJS(WEBSOCKET_URL);
const stompClient = Stomp.over(socket);

// Disable debug logs (optional)
stompClient.debug = null;

// Connect with JWT authentication
stompClient.connect(
    {
        // Pass JWT token in Authorization header
        'Authorization': `Bearer ${JWT_TOKEN}`
    },
    (frame) => {
        console.log('[SUCCESS] Connected to WebSocket server');
        console.log(`[INFO] Session: ${frame.headers['session']}`);
        
        // Subscribe to message queue (receives MessageEvent in JSON format)
        stompClient.subscribe(`/user/queue/messages`, (message) => {
            const event = JSON.parse(message.body);
            
            console.log('\n[MESSAGE RECEIVED]');
            console.log(`Type: ${event.type}`);
            
            if (event.type === 'new_message') {
                const msg = event.event;
                console.log(`Message ID: ${msg.messageId}`);
                console.log(`Conversation: ${msg.conversationId}`);
                console.log(`From: ${msg.sender.username} (${msg.sender.userId})`);
                console.log(`Text: ${msg.messageText}`);
                console.log(`Timestamp: ${new Date(msg.timestamp * 1000).toISOString()}`);
                console.log(`Sequence: ${msg.sequenceNumber}`);
            } else if (event.type === 'status_update') {
                const update = event.event;
                console.log(`Message ID: ${update.messageId}`);
                console.log(`Status: ${update.oldStatus} → ${update.newStatus}`);
                console.log(`Recipient: ${update.recipientId}`);
                console.log(`Timestamp: ${new Date(update.timestamp * 1000).toISOString()}`);
            }
            
            console.log('[END MESSAGE]\n');
        }, (error) => {
            console.error('[ERROR] Failed to subscribe:', error);
        });
        
        // Send subscription request to server
        console.log('[INFO] Sending subscribe request...');
        stompClient.send('/app/chat.subscribe', {}, userId);
        
        console.log('\n[READY] WebSocket client ready to receive messages');
        console.log('Listening for messages...');
        console.log('Press Ctrl+C to exit\n');
    },
    (error) => {
        console.error('[ERROR] WebSocket connection failed:', error);
        process.exit(1);
    }
);

// Handle disconnection
socket.onclose = () => {
    console.log('\n[INFO] WebSocket connection closed');
    process.exit(0);
};

// Handle process termination
process.on('SIGINT', () => {
    console.log('\n[INFO] Disconnecting...');
    stompClient.disconnect(() => {
        console.log('[INFO] Disconnected from WebSocket server');
        process.exit(0);
    });
});
