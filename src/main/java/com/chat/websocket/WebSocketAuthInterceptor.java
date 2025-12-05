package com.chat.websocket;

import com.chat.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * WebSocket Authentication Interceptor
 * 
 * Purpose: Validate JWT token on WebSocket CONNECT and extract userId.
 * Mirrors functionality of gRPC AuthenticationInterceptor for consistency.
 * 
 * Security Flow:
 * 1. Client sends STOMP CONNECT frame with Authorization header
 * 2. Interceptor extracts JWT token from "Authorization: Bearer {token}"
 * 3. JwtService validates token signature and expiration
 * 4. Extract userId from JWT claims
 * 5. Store userId in WebSocket session (used for user-specific destinations)
 * 6. If invalid token → reject connection with 401 Unauthorized
 * 
 * Distributed Systems Concept:
 * - Stateless authentication (JWT) enables horizontal scaling
 * - No server-side session storage (token contains all auth info)
 * - Same token works for REST, gRPC, and WebSocket
 * 
 * Thread Safety: Spring ensures interceptor is called sequentially per session.
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    
    private final JwtService jwtService;
    
    public WebSocketAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }
    
    /**
     * Intercept messages before sending to message channel.
     * 
     * Called for every STOMP frame:
     * - CONNECT: Initial WebSocket connection (validate JWT here)
     * - SUBSCRIBE: Client subscribes to destination
     * - SEND: Client sends message
     * - DISCONNECT: Client closes connection
     * 
     * @param message STOMP message
     * @param channel Message channel
     * @return message (modified with authentication principal) or null to reject
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Extract Authorization header from STOMP CONNECT frame
            String authHeader = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
            
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                logger.warn("WebSocket CONNECT rejected: Missing or invalid Authorization header");
                throw new IllegalArgumentException("Missing Authorization header");
            }
            
            String token = authHeader.substring(BEARER_PREFIX.length());
            
            try {
                // Validate JWT token
                if (!jwtService.validateToken(token)) {
                    logger.warn("WebSocket CONNECT rejected: Invalid JWT token");
                    throw new IllegalArgumentException("Invalid JWT token");
                }
                
                // Extract userId from JWT claims
                String userId = jwtService.extractUserId(token);
                String username = jwtService.extractUsername(token);
                
                if (userId == null || username == null) {
                    logger.warn("WebSocket CONNECT rejected: JWT missing userId or username");
                    throw new IllegalArgumentException("JWT missing required claims");
                }
                
                // Create Spring Security authentication principal
                // This enables @MessageMapping methods to access Principal parameter
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(
                        userId, // Principal name (userId for consistency with gRPC)
                        null,   // Credentials (not needed for token auth)
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                
                // Store authentication in WebSocket session
                accessor.setUser(authentication);
                
                logger.info("WebSocket CONNECT authenticated: userId={}, username={}", userId, username);
                
            } catch (Exception e) {
                logger.error("WebSocket authentication failed: {}", e.getMessage());
                throw new IllegalArgumentException("Authentication failed: " + e.getMessage());
            }
        }
        
        return message;
    }
}
