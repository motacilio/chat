package com.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for complete upload response.
 * (User Story 4 - P2: POST /api/files/complete response)
 * 
 * Confirms upload completion and provides file message details.
 * After upload completes, a Message entity is created with fileMetadata
 * and published to Kafka for async delivery.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompleteUploadResponse {
    
    /**
     * Message UUID created for this file.
     * This message follows the same lifecycle as text messages (SENT → DELIVERED → READ).
     */
    private String messageId;
    
    /**
     * File UUID.
     */
    private String fileId;
    
    /**
     * Message state ("SENT" when file message is published to Kafka).
     */
    private String state;
    
    /**
     * Upload status ("COMPLETED" or "FAILED").
     */
    private String uploadStatus;
    
    /**
     * Original filename.
     */
    private String filename;
    
    /**
     * File size in bytes.
     */
    private Long sizeBytes;
    
    /**
     * MIME type.
     */
    private String mimeType;
    
    /**
     * Upload completion timestamp (ISO 8601).
     */
    private String uploadedAt;
    
    /**
     * Conversation ID where file was uploaded.
     */
    private String conversationId;
    
    /**
     * Pre-signed download URL (valid for 1 hour per FR-023).
     * Recipients can use this to download the file.
     */
    private String downloadUrl;
    
    /**
     * Download URL expiration timestamp (ISO 8601).
     */
    private String downloadExpiresAt;
}
