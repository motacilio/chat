package com.chat.model;

/**
 * Message state enum (FR-007)
 * 
 * Represents the lifecycle of a message: SENT → DELIVERED → READ
 * 
 * Educational Note: State machine pattern demonstrates eventual consistency in distributed systems.
 * Messages progress through states asynchronously - SENT when persisted in MongoDB, DELIVERED when
 * reached recipient's device, READ when opened in conversation view.
 */
public enum MessageStatus {
    /**
     * SENT: Message accepted by server and persisted in MongoDB.
     * Initial state after successful submission via ChatService.SendMessage.
     */
    SENT,
    
    /**
     * DELIVERED: Message reached recipient's device.
     * Transition occurs when online user receives message via gRPC stream,
     * or when offline user reconnects and fetches pending messages.
     */
    DELIVERED,
    
    /**
     * READ: Message opened by recipient in conversation view.
     * Transition occurs when recipient calls ChatService.MarkMessageAsRead.
     */
    READ
}
