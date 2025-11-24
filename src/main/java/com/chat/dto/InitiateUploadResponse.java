package com.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for initiate upload response.
 * (User Story 4 - P2: POST /api/files/initiate response)
 * 
 * Contains pre-signed upload URL and file metadata for client to proceed with upload.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InitiateUploadResponse {
    
    /**
     * Server-generated file UUID.
     * Client uses this to complete upload and reference file in messages.
     */
    private String fileId;
    
    /**
     * Pre-signed PUT URL for direct upload to MinIO.
     * Client should PUT file content to this URL.
     * Expires in 1 hour.
     */
    private String uploadUrl;
    
    /**
     * Upload URL expiration timestamp (ISO 8601).
     */
    private String uploadExpiresAt;
    
    /**
     * Recommended chunk size for resumable upload (bytes).
     * Default: 5 MB (5,242,880 bytes).
     */
    private Long chunkSizeBytes;
    
    /**
     * Total number of chunks expected.
     * Calculated as ceil(sizeBytes / chunkSizeBytes).
     */
    private Integer totalChunks;
    
    /**
     * Whether resumable protocol is supported.
     * Always true for MinIO backend.
     */
    @Builder.Default
    private Boolean resumable = true;
}
