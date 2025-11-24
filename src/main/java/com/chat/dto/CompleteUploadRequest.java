package com.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for completing file upload.
 * (User Story 4 - P2: POST /api/files/complete)
 * 
 * Client submits this after finishing upload to MinIO to finalize file metadata
 * and create a file message that will be delivered through Kafka.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompleteUploadRequest {
    
    /**
     * File UUID (received from initiate upload response).
     */
    @NotBlank(message = "File ID is required")
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$", 
             message = "Invalid file ID format (must be UUID)")
    private String fileId;
    
    /**
     * MD5 checksum (hex string) of uploaded file.
     * Server validates integrity by comparing with actual file.
     */
    @NotBlank(message = "Checksum MD5 is required")
    private String checksumMd5;
    
    /**
     * Sender user ID (extracted from JWT in controller).
     * Required to create file message.
     */
    @NotBlank(message = "Sender ID is required")
    private String senderId;
    
    /**
     * Recipient user IDs for the file message.
     * Required to create message and trigger delivery via Kafka.
     */
    @NotEmpty(message = "At least one recipient is required")
    private List<String> recipientIds;
}
