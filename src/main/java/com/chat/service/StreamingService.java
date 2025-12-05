package com.chat.service;

import com.chat.grpc.v1.MessageEvent;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * StreamingService - Manages active streaming connections for real-time message delivery.
 * 
 * Responsibility: Tracks active user streams, routes messages/events to online users, manages connection lifecycle.
 * Does NOT: Handle message persistence (see MessageService), manage offline delivery (see MessageDeliveryWorker).
 * 
 * Distributed Systems Concept: This implements the "online user" delivery path.
 * - Online users: Messages pushed via gRPC OR WebSocket stream (real-time, <100ms latency per NFR-003)
 * - Offline users: Messages delivered via Kafka workers when they reconnect
 * 
 * Multi-Transport Support:
 * - gRPC Streaming: Backend-to-backend (microservices, high performance)
 * - WebSocket/STOMP: Client-to-backend (mobile apps, web browsers, NAT traversal)
 * - Same delivery logic, different transports
 * 
 * Why Both Paths?
 * - Real-time streaming: Low latency for online users, immediate feedback
 * - Kafka async: Guaranteed delivery for offline users, durability, replay capability
 * 
 * This is a "best of both worlds" hybrid: real-time when possible, guaranteed delivery always.
 * 
 * Concurrency Safety: Uses ConcurrentHashMap to safely manage streams across multiple threads.
 * MessageDeliveryWorker and MessageStateUpdateWorker both call this service concurrently.
 */
@Service
public class StreamingService {
    
    private static final Logger logger = LoggerFactory.getLogger(StreamingService.class);
    
    // Active gRPC streams: user_id -> StreamObserver
    // Why ConcurrentHashMap? Multiple Kafka consumer threads may call notifyUserMessage concurrently
    private final Map<String, StreamObserver<MessageEvent>> grpcMessageStreams = new ConcurrentHashMap<>();
    
    // Active WebSocket streams: user_id -> Consumer callback
    // Consumer accepts MessageEvent and pushes to WebSocket client via SimpMessagingTemplate
    private final Map<String, Consumer<MessageEvent>> webSocketMessageStreams = new ConcurrentHashMap<>();
    
    /**
     * Subscribe user to gRPC message stream (called when client initiates StreamMessages RPC).
     * 
     * Flow:
     * 1. Client calls ChatService.StreamMessages(StreamMessagesRequest)
     * 2. ChatServiceImpl calls this method to register stream
     * 3. MessageDeliveryWorker checks if user is online, pushes via stream if active
     * 
     * @param userId User identifier
     * @param responseObserver gRPC response stream to push MessageEvent
     */
    public void subscribeToMessages(String userId, StreamObserver<MessageEvent> responseObserver) {
        grpcMessageStreams.put(userId, responseObserver);
        logger.info("User {} subscribed to gRPC message stream (total active gRPC streams: {})", 
                userId, grpcMessageStreams.size());
    }
    
    /**
     * Unsubscribe user from gRPC message stream (called when client disconnects or closes stream).
     * 
     * @param userId User identifier
     */
    public void unsubscribeFromMessages(String userId) {
        grpcMessageStreams.remove(userId);
        logger.info("User {} unsubscribed from gRPC message stream (remaining gRPC streams: {})", 
                userId, grpcMessageStreams.size());
    }
    
    /**
     * Subscribe user to WebSocket message stream (called when client connects via WebSocket).
     * 
     * Flow:
     * 1. Client connects to /ws endpoint
     * 2. Client sends STOMP CONNECT with JWT token
     * 3. WebSocketAuthInterceptor validates token
     * 4. Client sends STOMP SEND to /app/chat.subscribe
     * 5. WebSocketMessageController calls this method to register stream
     * 6. MessageDeliveryWorker pushes via callback when message arrives
     * 
     * @param userId User identifier
     * @param messageConsumer Callback to push MessageEvent to WebSocket client
     */
    public void subscribeToMessagesWebSocket(String userId, Consumer<MessageEvent> messageConsumer) {
        webSocketMessageStreams.put(userId, messageConsumer);
        logger.info("User {} subscribed to WebSocket message stream (total active WebSocket streams: {})", 
                userId, webSocketMessageStreams.size());
    }
    
    /**
     * Unsubscribe user from WebSocket message stream (called when client disconnects).
     * 
     * @param userId User identifier
     */
    public void unsubscribeFromMessagesWebSocket(String userId) {
        webSocketMessageStreams.remove(userId);
        logger.info("User {} unsubscribed from WebSocket message stream (remaining WebSocket streams: {})", 
                userId, webSocketMessageStreams.size());
    }
    
    /**
     * Push message or status update to user's active streams (gRPC or WebSocket) if online.
     * 
     * Multi-Transport Delivery:
     * - Tries gRPC stream first (for backend microservices, high performance)
     * - Tries WebSocket stream second (for mobile/web clients behind NAT)
     * - If user has both connections, delivers to both
     * - Returns true if delivered to at least one transport
     * 
     * Flow for new messages:
     * 1. MessageDeliveryWorker persists message to MongoDB
     * 2. Checks if recipient is online via this method
     * 3. If online: Push via stream (real-time delivery)
     * 4. If offline: User will fetch via GetConversationHistory when they reconnect
     * 
     * Flow for status updates:
     * 1. Recipient marks message as read via MarkMessageAsRead RPC
     * 2. MessageStateUpdateWorker processes state change
     * 3. Notifies sender via this method (if sender is online)
     * 
     * Performance: This is O(1) lookup in ConcurrentHashMap - no database query needed.
     * 
     * @param userId Recipient user ID (for messages) or sender user ID (for status updates)
     * @param event MessageEvent to push (can contain NewMessageEvent or StatusUpdateEvent)
     * @return true if message was pushed to at least one active stream, false if user offline on all transports
     */
    public boolean notifyUserMessage(String userId, MessageEvent event) {
        boolean deliveredViaGrpc = false;
        boolean deliveredViaWebSocket = false;
        
        // Try gRPC stream (for backend microservices)
        StreamObserver<MessageEvent> grpcStream = grpcMessageStreams.get(userId);
        if (grpcStream != null) {
            try {
                grpcStream.onNext(event);
                deliveredViaGrpc = true;
                
                // Log based on event type
                if (event.hasNewMessage()) {
                    logger.debug("Pushed new message {} to user {} via gRPC stream", 
                            event.getNewMessage().getMessageId(), userId);
                } else if (event.hasStatusUpdate()) {
                    logger.debug("Pushed status update for message {} to user {} via gRPC stream", 
                            event.getStatusUpdate().getMessageId(), userId);
                }
            } catch (Exception e) {
                // gRPC stream is broken - remove it
                logger.warn("Failed to push to gRPC stream for user {} - removing stream: {}", 
                        userId, e.getMessage());
                grpcMessageStreams.remove(userId);
            }
        }
        
        // Try WebSocket stream (for mobile/web clients behind NAT)
        Consumer<MessageEvent> wsCallback = webSocketMessageStreams.get(userId);
        if (wsCallback != null) {
            try {
                wsCallback.accept(event);
                deliveredViaWebSocket = true;
                
                // Log based on event type
                if (event.hasNewMessage()) {
                    logger.debug("Pushed new message {} to user {} via WebSocket stream", 
                            event.getNewMessage().getMessageId(), userId);
                } else if (event.hasStatusUpdate()) {
                    logger.debug("Pushed status update for message {} to user {} via WebSocket stream", 
                            event.getStatusUpdate().getMessageId(), userId);
                }
            } catch (Exception e) {
                // WebSocket stream is broken - remove it
                logger.warn("Failed to push to WebSocket stream for user {} - removing stream: {}", 
                        userId, e.getMessage());
                webSocketMessageStreams.remove(userId);
            }
        }
        
        // If delivered to at least one transport, return true
        boolean delivered = deliveredViaGrpc || deliveredViaWebSocket;
        
        if (!delivered) {
            logger.debug("User {} is offline on all transports - event will be delivered via history query", userId);
        }
        
        return delivered;
    }
    
    /**
     * Check if user has active stream on any transport (gRPC or WebSocket).
     * 
     * @param userId User identifier
     * @return true if user is subscribed to gRPC stream OR WebSocket stream
     */
    public boolean isUserOnline(String userId) {
        return grpcMessageStreams.containsKey(userId) || 
               webSocketMessageStreams.containsKey(userId);
    }
    
    /**
     * Get count of active gRPC message streams (for monitoring/metrics).
     * 
     * @return Number of active gRPC user streams
     */
    public int getActiveGrpcStreamCount() {
        return grpcMessageStreams.size();
    }
    
    /**
     * Get count of active WebSocket message streams (for monitoring/metrics).
     * 
     * @return Number of active WebSocket user streams
     */
    public int getActiveWebSocketStreamCount() {
        return webSocketMessageStreams.size();
    }
    
    /**
     * Get total count of active message streams across all transports (for monitoring/metrics).
     * 
     * NOTE: If a user is connected via both gRPC and WebSocket, they are counted twice.
     * Use isUserOnline() to check unique online users.
     * 
     * @return Total number of active streams (gRPC + WebSocket)
     */
    public int getActiveMessageStreamCount() {
        return grpcMessageStreams.size() + webSocketMessageStreams.size();
    }
}
