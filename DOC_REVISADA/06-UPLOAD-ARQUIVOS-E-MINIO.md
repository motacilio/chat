# 06 - Upload de Arquivos e MinIO

**Versão**: 1.0  
**Status**: ✅ Implementado

---

## Visão Geral

### Objetivo

Upload/download de arquivos até **2 GB** usando MinIO (S3-compatible storage).

### Endpoints REST

| Endpoint | Método | Propósito |
|----------|--------|-----------|
| `/api/files/upload` | POST (multipart) | Upload de arquivo |
| `/api/files/{fileId}` | GET | Download de arquivo |
| `/api/files/{fileId}/metadata` | GET | Metadados sem download |

---

## MinIO Configuration

**Arquivo**: `src/main/java/com/chat/config/MinioConfig.java`

```java
@Configuration
public class MinioConfig {
    
    @Value("${minio.url}")
    private String minioUrl;
    
    @Value("${minio.access-key}")
    private String accessKey;
    
    @Value("${minio.secret-key}")
    private String secretKey;
    
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(accessKey, secretKey)
                .build();
    }
}
```

**application.yml**:
```yaml
minio:
  url: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket-name: chat-files
```

---

## Upload Endpoint

**Arquivo**: `src/main/java/com/chat/controller/FileController.java`

```java
@RestController
@RequestMapping("/api/files")
public class FileController {
    
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("conversationId") String conversationId,
            @RequestParam("senderId") String senderId) {
        
        // Valida tamanho (max 2GB)
        if (file.getSize() > 2L * 1024 * 1024 * 1024) {
            throw new IllegalArgumentException("File exceeds 2GB limit");
        }
        
        // Gera UUID para arquivo
        String fileId = UUID.randomUUID().toString();
        
        // Upload para MinIO
        FileMetadata metadata = fileStorageService.uploadFile(
            fileId, 
            file.getInputStream(), 
            file.getSize(), 
            file.getContentType(),
            file.getOriginalFilename()
        );
        
        // Publica evento Kafka (MessageEvent com file_id)
        MessageEvent event = MessageEvent.newBuilder()
                .setMessageId(UUID.randomUUID().toString())
                .setConversationId(conversationId)
                .setSenderId(senderId)
                .setFileId(fileId)  // Em vez de message_text
                .build();
        
        messageKafkaTemplate.send("message-events", conversationId, event);
        
        return ResponseEntity.ok(FileUploadResponse.builder()
                .fileId(fileId)
                .filename(metadata.getFilename())
                .sizeBytes(metadata.getSizeBytes())
                .build());
    }
}
```

---

## FileStorageService

**Arquivo**: `src/main/java/com/chat/service/FileStorageService.java`

```java
@Service
public class FileStorageService {
    
    @Autowired
    private MinioClient minioClient;
    
    @Value("${minio.bucket-name}")
    private String bucketName;
    
    public FileMetadata uploadFile(String fileId, InputStream inputStream, 
                                   long size, String contentType, String filename) {
        try {
            // Cria bucket se não existe
            createBucketIfNotExists();
            
            // Upload para MinIO
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileId)  // Nome do objeto = UUID
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build()
            );
            
            // Retorna metadata
            return FileMetadata.builder()
                    .fileId(fileId)
                    .filename(filename)
                    .sizeBytes(size)
                    .mimeType(contentType)
                    .storageUrl("minio://" + bucketName + "/" + fileId)
                    .uploadedAt(Instant.now())
                    .build();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file", e);
        }
    }
    
    public InputStream downloadFile(String fileId) {
        try {
            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileId)
                    .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file", e);
        }
    }
}
```

---

## Decisões

### 1. MinIO vs S3

| MinIO (Escolhido) | AWS S3 |
|-------------------|--------|
| ✅ Self-hosted | ❌ Vendor lock-in |
| ✅ S3-compatible API | ✅ S3 API |
| ✅ Sem custos | ❌ Custo por GB |

**Decisão**: MinIO para demonstrar conceito sem AWS dependency

### 2. Chunked Upload (Futuro)

Para arquivos >100MB, implementar multipart upload:
```java
minioClient.composeObject(...)  // Combina parts
```

---

**Próximo**: [07-STATUS-E-STREAMING-TEMPO-REAL.md](07-STATUS-E-STREAMING-TEMPO-REAL.md)
