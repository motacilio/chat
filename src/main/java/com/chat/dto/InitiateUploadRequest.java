package com.chat.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for initiating file upload.
 * (User Story 4 - P2: POST /api/files/initiate)
 * 
 * Client submits this to start upload process and receive pre-signed URL.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InitiateUploadRequest {
    
    /**
     * Conversation UUID where file will be shared.
     * Must be valid UUID format (RFC 4122).
     */
    @NotBlank(message = "Conversation ID is required")
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$", 
             message = "Invalid conversation ID format (must be UUID)")
    private String conversationId;
    
    /**
     * Original filename (e.g., "document.pdf").
     */
    @NotBlank(message = "Filename is required")
    private String filename;
    
    /**
     * File size in bytes.
     * Must be ≤ 2,147,483,648 (2 GB per FR-024).
     */
    @NotNull(message = "File size is required")
    @Min(value = 1, message = "File size must be greater than 0")
    private Long sizeBytes;
    
    /**
     * MIME type (e.g., "application/pdf", "image/png").
     */
    @NotBlank(message = "MIME type is required")
    private String mimeType;
    
    /**
     * Optional: MD5 checksum (hex string) calculated by client.
     * Used for integrity validation on upload completion.
     */
    private String checksumMd5;
}
