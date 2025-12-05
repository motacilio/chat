package com.chat.websocket;

import com.chat.grpc.v1.MessageEvent;
import com.chat.grpc.v1.NewMessageEvent;
import com.chat.grpc.v1.StatusUpdateEvent;
import com.chat.model.Conversation;
import com.chat.repository.ConversationRepository;
import com.chat.service.StreamingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * WebSocket Notification Controller
 * 
 * Purpose: Handle real-time notifications via WebSocket for:
 * 1. Status updates (delivered/read) - Notifies message sender
 * 2. Group messages - Notifies all participants in conversation
 * 3. External platform updates (WhatsApp/Telegram) - Push to internal users
 * 
 * Architecture:
 * - Individual messages: /user/{userId}/queue/messages
 * - Group notifications: /topic/conversation/{conversationId}
 * - Status updates: /user/{senderId}/queue/status
 * 
 * Why WebSocket for Notifications?
 * - Real-time delivery (<100ms latency)
 * - Works through NAT/firewall (unlike webhooks)
 * - Reduces polling (battery/bandwidth savings)
 * - Better UX (instant read receipts, typing indicators)
 * 
 * Integration with External Platforms:
 * - WhatsApp/Telegram send webhook to server (requires ngrok/Cloudflare tunnel)
 * - WebhookController processes update
 * - Publishes to Kafka (state-update-events)
 * - MessageStateUpdateWorker updates MongoDB
 * - StreamingService pushes to WebSocket clients
 */
@Controller
public class WebSocketNotificationController {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketNotificationController.class);
    
    private final StreamingService streamingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;
    
    public WebSocketNotificationController(
            StreamingService streamingService,
            SimpMessagingTemplate messagingTemplate,
            ConversationRepository conversationRepository,
            ObjectMapper objectMapper) {
        this.streamingService = streamingService;
        this.messagingTemplate = messagingTemplate;
        this.conversationRepository = conversationRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Subscribe to personal message notifications (1-on-1 and group messages).
     * 
     * Destination: /app/notifications.subscribe
     * Client sends: userId
     * 
     * Receives:
     * - New messages in 1-on-1 conversations
     * - New messages in group conversations
     * - Status updates for messages user sent (delivered/read)
     * 
     * @param userId User ID to subscribe
     * @param principal Authenticated user (from JWT)
     */
    @MessageMapping("/notifications.subscribe")
    public void subscribeToNotifications(@Payload String userId, Principal principal) {
        String authenticatedUserId = principal.getName();
        
        // SECURITY: Validate userId matches authenticated user
        if (!userId.equals(authenticatedUserId)) {
            logger.error("SECURITY: WebSocket user_id mismatch - token userId: {}, request userId: {}", 
                        authenticatedUserId, userId);
            throw new IllegalArgumentException("user_id must match authenticated user");
        }
        
        // Register WebSocket listener in StreamingService
        streamingService.subscribeToMessagesWebSocket(userId, (event) -> {
            try {
                String jsonEvent = convertMessageEventToJson(event);
                
                if (event.hasNewMessage()) {
                    // New message notification
                    messagingTemplate.convertAndSendToUser(userId, "/queue/messages", jsonEvent);
                    logger.debug("Pushed new message notification to user: {}", userId);
                    
                } else if (event.hasStatusUpdate()) {
                    // Status update notification (for message sender)
                    messagingTemplate.convertAndSendToUser(userId, "/queue/status", jsonEvent);
                    logger.debug("Pushed status update notification to user: {}", userId);
                }
                
            } catch (Exception e) {
                logger.error("Failed to push notification to WebSocket user: {}", userId, e);
            }
        });
        
        logger.info("User {} subscribed to WebSocket notifications", userId);
    }
    
    /**
     * Subscribe to group conversation notifications.
     * 
     * Destination: /app/group.subscribe
     * Client sends: { "conversationId": "conv-123" }
     * 
     * All participants in the conversation will receive:
     * - New messages in the group
     * - Member join/leave events (future)
     * - Typing indicators (future)
     * 
     * Note: Uses /topic/{conversationId} for broadcast to all subscribers
     * 
     * @param conversationId Conversation ID to subscribe
     * @param principal Authenticated user
     */
    @MessageMapping("/group.subscribe")
    public void subscribeToGroup(@Payload String conversationId, Principal principal) {
        String userId = principal.getName();
        
        // SECURITY: Validate user is participant in conversation
        Optional<Conversation> conversationOpt = conversationRepository.findByConversationId(conversationId);
        
        if (conversationOpt.isEmpty()) {
            logger.error("Conversation not found: {}", conversationId);
            throw new IllegalArgumentException("Conversation not found");
        }
        
        Conversation conversation = conversationOpt.get();
        
        if (!conversation.getParticipants().contains(userId)) {
            logger.error("SECURITY: User {} attempted to subscribe to conversation {} without being participant", 
                        userId, conversationId);
            throw new IllegalArgumentException("User not authorized for this conversation");
        }
        
        // Client should also subscribe to: /topic/conversation/{conversationId}
        // Server will broadcast messages to this topic
        // No server-side registration needed (STOMP broker handles subscription)
        
        logger.info("User {} subscribed to group conversation: {}", userId, conversationId);
    }
    
    /**
     * Broadcast message to all participants in a group conversation.
     * 
     * Called internally by MessageDeliveryWorker when processing group messages.
     * 
     * Flow:
     * 1. MessageDeliveryWorker detects conversation type is GROUP
     * 2. Calls this method with message event
     * 3. Server broadcasts to /topic/conversation/{conversationId}
     * 4. All subscribed clients receive notification simultaneously
     * 
     * @param conversationId Conversation ID
     * @param event MessageEvent (NewMessageEvent or StatusUpdateEvent)
     */
    public void broadcastToGroup(String conversationId, MessageEvent event) {
        try {
            String jsonEvent = convertMessageEventToJson(event);
            
            // Broadcast to all subscribers of /topic/conversation/{conversationId}
            messagingTemplate.convertAndSend("/topic/conversation/" + conversationId, jsonEvent);
            
            logger.debug("Broadcast message to group conversation: {}", conversationId);
            
        } catch (Exception e) {
            logger.error("Failed to broadcast to group conversation: {}", conversationId, e);
        }
    }
    
    /**
     * Push status update to message sender (read receipt).
     * 
     * Called by MessageStateUpdateWorker when message status changes.
     * 
     * Flow:
     * 1. Recipient marks message as READ via MarkMessageAsRead RPC
     * 2. ChatServiceImpl publishes to Kafka (state-update-events)
     * 3. MessageStateUpdateWorker updates MongoDB
     * 4. MessageStateUpdateWorker calls StreamingService.notifyUserMessage()
     * 5. StreamingService routes to WebSocket or gRPC
     * 6. This method delivers to /user/{senderId}/queue/status
     * 
     * @param senderId Message sender user ID
     * @param event StatusUpdateEvent
     */
    public void pushStatusUpdateToSender(String senderId, MessageEvent event) {
        if (!event.hasStatusUpdate()) {
            logger.warn("Attempted to push non-status-update event to sender: {}", senderId);
            return;
        }
        
        try {
            String jsonEvent = convertMessageEventToJson(event);
            
            // Push to sender's status queue
            messagingTemplate.convertAndSendToUser(senderId, "/queue/status", jsonEvent);
            
            logger.debug("Pushed status update to sender: {}", senderId);
            
        } catch (Exception e) {
            logger.error("Failed to push status update to sender: {}", senderId, e);
        }
    }
    
    /**
     * Convert Protobuf MessageEvent to JSON for WebSocket clients.
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
