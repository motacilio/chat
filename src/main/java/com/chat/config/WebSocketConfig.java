package com.chat.config;

import com.chat.websocket.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket Configuration for Real-Time Messaging
 * 
 * Purpose: Enable WebSocket as alternative transport to gRPC streaming.
 * Solves: NAT/firewall traversal for mobile/web clients (gRPC requires direct connection).
 * 
 * Architecture Decision:
 * - gRPC Streaming: Backend-to-backend communication (microservices)
 * - WebSocket/STOMP: Client-to-backend communication (mobile apps, web browsers)
 * - Both use same StreamingService for unified message delivery
 * 
 * Protocol: STOMP over WebSocket
 * - STOMP (Simple Text Oriented Messaging Protocol) provides pub/sub semantics
 * - Similar to gRPC streaming but works over HTTP/WebSocket (traverses NAT)
 * 
 * Endpoints:
 * - /ws: WebSocket handshake endpoint (HTTP → WebSocket upgrade)
 * - /app: Application destination prefix (client sends to /app/chat.subscribe)
 * - /topic: Broadcast messages (group conversations)
 * - /queue: Point-to-point messages (1:1 conversations, user-specific notifications)
 * 
 * Security:
 * - JWT token required in STOMP CONNECT frame (Authorization header)
 * - WebSocketAuthInterceptor validates token before establishing connection
 * - Same security model as gRPC (AuthenticationInterceptor)
 * 
 * Distributed Systems Concept:
 * - WebSocket maintains stateful connection (like gRPC streaming)
 * - SockJS provides fallback to polling if WebSocket blocked
 * - Horizontal scaling requires sticky sessions OR external message broker (Redis)
 * 
 * See: docs/GUIA-STREAMING-TEMPO-REAL.md (Soluções para NAT/Firewall section)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    
    public WebSocketConfig(WebSocketAuthInterceptor webSocketAuthInterceptor) {
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
    }
    
    /**
     * Configure message broker for pub/sub semantics.
     * 
     * Simple Broker (current): In-memory broker for single-instance deployment
     * - Fast, no external dependencies
     * - Does NOT scale horizontally (messages only on single server instance)
     * 
     * Production: Replace with external broker for multi-instance deployment:
     * - RabbitMQ: config.enableStompBrokerRelay("/topic", "/queue")
     * - Redis: Use Spring Session + Redis for WebSocket session sharing
     * 
     * Destinations:
     * - /topic/messages.{conversationId} - Group conversation messages (broadcast)
     * - /queue/messages - User-specific messages (point-to-point)
     * - /queue/status - Status updates (DELIVERED, READ receipts)
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple in-memory broker (for POC/single instance)
        config.enableSimpleBroker("/topic", "/queue");
        
        // Application destination prefix (client sends to /app/chat.subscribe)
        config.setApplicationDestinationPrefixes("/app");
        
        // User destination prefix (server sends to /user/{userId}/queue/messages)
        config.setUserDestinationPrefix("/user");
    }
    
    /**
     * Register STOMP endpoints for WebSocket handshake.
     * 
     * Endpoint: /ws
     * - HTTP → WebSocket protocol upgrade
     * - Client connects: ws://localhost:8081/ws
     * 
     * SockJS Fallback:
     * - If WebSocket blocked (corporate proxy, old browsers), falls back to:
     *   1. HTTP streaming
     *   2. HTTP long polling
     * - Provides compatibility with 99% of clients
     * 
     * CORS: Allow all origins (*) for POC. Production should restrict:
     * - .setAllowedOrigins("https://yourdomain.com")
     * - Or use .setAllowedOriginPatterns("https://*.yourdomain.com")
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // CORS: Allow all origins (POC only)
                .withSockJS(); // Enable SockJS fallback for compatibility
    }
    
    /**
     * Configure inbound channel for JWT authentication.
     * 
     * Intercepts STOMP frames BEFORE processing:
     * - CONNECT frame: Validate JWT token from Authorization header
     * - SUBSCRIBE frame: Validate user can subscribe to destination
     * - SEND frame: Validate user can send to destination
     * 
     * WebSocketAuthInterceptor extracts userId from JWT token and stores in session.
     * This userId is used for user-specific destinations (/user/{userId}/queue/messages).
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
