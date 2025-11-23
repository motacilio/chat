package com.chat.service;

import com.chat.grpc.v1.MessageEvent;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StreamingService - Manages active gRPC bidirectional streams for real-time message delivery.
 * 
 * Responsibility: Tracks active user streams, routes messages/events to online users, manages connection lifecycle.
 * Does NOT: Handle message persistence (see MessageService), manage offline delivery (see MessageDeliveryWorker).
 * 
 * Distributed Systems Concept: This implements the "online user" delivery path.
 * - Online users: Messages pushed via gRPC stream (real-time, <100ms latency per NFR-003)
 * - Offline users: Messages delivered via Kafka workers when they reconnect
 * 
 * Why Both Paths?
 * - gRPC streaming: Low latency for online users, immediate feedback
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
    
    // Active streams: user_id -> StreamObserver
    // Why ConcurrentHashMap? Multiple Kafka consumer threads may call notifyUserMessage concurrently
    private final Map<String, StreamObserver<MessageEvent>> messageStreams = new ConcurrentHashMap<>();
    
    /**
     * Subscribe user to message stream (called when client initiates StreamMessages RPC).
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
        messageStreams.put(userId, responseObserver);
        logger.info("User {} subscribed to message stream (total active streams: {})", 
                userId, messageStreams.size());
    }
    
    /**
     * Unsubscribe user from message stream (called when client disconnects or closes stream).
     * 
     * @param userId User identifier
     */
    public void unsubscribeFromMessages(String userId) {
        messageStreams.remove(userId);
        logger.info("User {} unsubscribed from message stream (remaining streams: {})", 
                userId, messageStreams.size());
    }
    
    /**
     * Push message or status update to user's stream if online (called by MessageDeliveryWorker and MessageStateUpdateWorker).
     * 
     * Flow:
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
     * @return true if message was pushed to active stream, false if user offline
     */
    public boolean notifyUserMessage(String userId, MessageEvent event) {
        StreamObserver<MessageEvent> stream = messageStreams.get(userId);
        
        if (stream == null) {
            logger.debug("User {} is offline - event will be delivered via history query", userId);
            return false;
        }
        
        try {
            stream.onNext(event);
            
            // Log based on event type
            if (event.hasNewMessage()) {
                logger.debug("Pushed new message {} to user {} stream", 
                        event.getNewMessage().getMessageId(), userId);
            } else if (event.hasStatusUpdate()) {
                logger.debug("Pushed status update for message {} to user {} stream", 
                        event.getStatusUpdate().getMessageId(), userId);
            }
            
            return true;
        } catch (Exception e) {
            logger.warn("Failed to push event to user {} stream - removing stream", userId, e);
            unsubscribeFromMessages(userId);
            return false;
        }
    }
    
    /**
     * Check if user has active message stream (online status).
     * 
     * @param userId User identifier
     * @return true if user is subscribed to message stream
     */
    public boolean isUserOnline(String userId) {
        return messageStreams.containsKey(userId);
    }
    
    /**
     * Get count of active message streams (for monitoring/metrics).
     * 
     * @return Number of active user streams
     */
    public int getActiveMessageStreamCount() {
        return messageStreams.size();
    }
}
