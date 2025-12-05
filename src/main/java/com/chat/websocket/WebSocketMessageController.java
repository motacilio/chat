package com.chat.websocket;

import com.chat.grpc.v1.MessageEvent;
import com.chat.grpc.v1.NewMessageEvent;
import com.chat.grpc.v1.StatusUpdateEvent;
import com.chat.service.StreamingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket Message Controller
 * 
 * Purpose: Handle WebSocket STOMP messages for real-time chat.
 * Integrates with existing StreamingService to unify gRPC and WebSocket delivery.
 * 
 * Architecture:
 * - Client sends: /app/chat.subscribe (STOMP SEND to application destination)
 * - Server sends: /user/{userId}/queue/messages (user-specific queue)
 * 
 * Flow:
 * 1. Client authenticates via JWT (WebSocketAuthInterceptor)
 * 2. Client sends SUBSCRIBE to /user/queue/messages
 * 3. Client sends SEND to /app/chat.subscribe with userId
 * 4. Controller registers user in StreamingService
 * 5. MessageDeliveryWorker/MessageStateUpdateWorker push events
 * 6. StreamingService routes to gRPC OR WebSocket (or both)
 * 7. Server pushes to /user/{userId}/queue/messages
 * 8. Client receives message in real-time
 * 
 * Why Both gRPC and WebSocket?
 * - gRPC: Backend-to-backend (microservices, high performance)
 * - WebSocket: Client-to-backend (mobile, web, NAT traversal)
 * - Same StreamingService, different transports
 * 
 * Security:
 * - Principal parameter contains userId from JWT (set by WebSocketAuthInterceptor)
 * - User can only subscribe to their own queue (/user/{userId}/queue/messages)
 * - STOMP authorization enforced by Spring Security WebSocket
 */
@Controller
public class WebSocketMessageController {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketMessageController.class);
    
    private final StreamingService streamingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    
    public WebSocketMessageController(
            StreamingService streamingService,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper) {
        this.streamingService = streamingService;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Subscribe to real-time messages via WebSocket.
     * 
     * Destination: /app/chat.subscribe
     * Client sends: { "userId": "a1a1a1a1-1111-1111-1111-111111111111" }
     * 
     * Security:
     * - Principal.getName() returns userId from JWT token
     * - Validates that request userId matches authenticated userId
     * - Prevents user from subscribing to another user's messages
     * 
     * Implementation:
     * - Registers user in StreamingService with WebSocket adapter
     * - StreamingService.notifyUserMessage() will push to WebSocket OR gRPC
     * - Connection stays open until client disconnects or session times out
     * 
     * @param userId User ID to subscribe (must match authenticated user)
     * @param principal Authenticated user (from JWT token)
     */
    @MessageMapping("/chat.subscribe")
    public void subscribeToMessages(@Payload String userId, Principal principal) {
        String authenticatedUserId = principal.getName();
        
        // SECURITY: Validate userId matches authenticated user
        if (!userId.equals(authenticatedUserId)) {
            logger.error("SECURITY: WebSocket user_id mismatch - token userId: {}, request userId: {}", 
                        authenticatedUserId, userId);
            throw new IllegalArgumentException("user_id must match authenticated user");
        }
        
        // Register WebSocket listener in StreamingService
        // StreamingService will route messages to this user via WebSocket
        streamingService.subscribeToMessagesWebSocket(userId, (event) -> {
            try {
                // Convert Protobuf MessageEvent to JSON for WebSocket clients
                String jsonEvent = convertMessageEventToJson(event);
                
                // Push to user-specific queue: /user/{userId}/queue/messages
                messagingTemplate.convertAndSendToUser(userId, "/queue/messages", jsonEvent);
                
                logger.debug("Pushed message to WebSocket user: {}", userId);
            } catch (Exception e) {
                logger.error("Failed to push message to WebSocket user: {}", userId, e);
            }
        });
        
        logger.info("User {} subscribed to WebSocket messages (active WebSocket streams: {})",
                userId, streamingService.getActiveWebSocketStreamCount());
    }
    
    /**
     * Unsubscribe from messages (called on disconnect).
     * 
     * Note: Spring WebSocket automatically calls this when client disconnects.
     * Manual unsubscribe not required, but provided for explicit cleanup.
     */
    @MessageMapping("/chat.unsubscribe")
    public void unsubscribeFromMessages(Principal principal) {
        String userId = principal.getName();
        streamingService.unsubscribeFromMessagesWebSocket(userId);
        
        logger.info("User {} unsubscribed from WebSocket messages", userId);
    }
    
    /**
     * Convert Protobuf MessageEvent to JSON for WebSocket clients.
     * 
     * WebSocket clients (JavaScript, mobile apps) expect JSON, not Protobuf.
     * This method serializes Protobuf to JSON for compatibility.
     * 
     * Alternative: Use Protobuf.js on client side to handle binary Protobuf
     * (more efficient but requires additional client library)
     */
    private String convertMessageEventToJson(MessageEvent event) throws Exception {
        if (event.hasNewMessage()) {
            NewMessageEvent newMsg = event.getNewMessage();
            return objectMapper.writeValueAsString(new MessageEventJson(
                "new_message",
                new NewMessageJson(
                    newMsg.getMessageId(),
                    newMsg.getConversationId(),
                    newMsg.getSender().getUserId(),
                    newMsg.getSender().getUsername(),
                    newMsg.getMessageText(),
                    newMsg.getTimestamp().getSeconds(),
                    newMsg.getSequenceNumber()
                )
            ));
        } else if (event.hasStatusUpdate()) {
            StatusUpdateEvent statusUpdate = event.getStatusUpdate();
            return objectMapper.writeValueAsString(new MessageEventJson(
                "status_update",
                new StatusUpdateJson(
                    statusUpdate.getMessageId(),
                    statusUpdate.getOldStatus().name(),
                    statusUpdate.getNewStatus().name(),
                    statusUpdate.getTimestamp().getSeconds(),
                    statusUpdate.getRecipientId()
                )
            ));
        }
        
        throw new IllegalArgumentException("Unknown MessageEvent type");
    }
    
    // JSON DTOs for WebSocket serialization
    
    record MessageEventJson(String type, Object event) {}
    
    record NewMessageJson(
        String messageId,
        String conversationId,
        String senderId,
        String senderUsername,
        String messageText,
        long timestamp,
        long sequenceNumber
    ) {}
    
    record StatusUpdateJson(
        String messageId,
        String oldStatus,
        String newStatus,
        long timestamp,
        String recipientId
    ) {}
}
