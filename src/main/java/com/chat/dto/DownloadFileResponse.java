package com.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for file download response.
 * (User Story 4 - P2: GET /api/files/{fileId}/download response)
 * 
 * Provides pre-signed download URL for direct client download from MinIO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownloadFileResponse {
    
    /**
     * File UUID.
     */
    private String fileId;
    
    /**
     * Pre-signed GET URL for direct download from MinIO.
     * Client should GET this URL to download file content.
     * Expires in 1 hour (per FR-023).
     */
    private String downloadUrl;
    
    /**
     * Download URL expiration timestamp (ISO 8601).
     */
    private String downloadExpiresAt;
    
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
}
