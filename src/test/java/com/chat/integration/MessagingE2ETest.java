package com.chat.integration;

import com.chat.dto.LoginRequest;
import com.chat.dto.LoginResponse;
import com.chat.grpc.v1.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Integration Test for Messaging Flow
 * 
 * Tests complete messaging lifecycle:
 * 1. Login (REST API) → JWT token
 * 2. CreateConversation (gRPC) → conversation_id
 * 3. SendMessage (gRPC) → SENT status
 * 4. Wait for Kafka processing
 * 5. GetMessageStatus → DELIVERED status
 * 6. MarkMessageAsRead → READ status
 * 7. GetMessageStatus → Complete state history
 * 8. GetConversationHistory → Message persistence
 * 
 * NOTE: Uses RANDOM_PORT to avoid conflict with running application on port 8081
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MessagingE2ETest {
    
    @LocalServerPort
    private int restPort; // Will be assigned random port by Spring
    
    private static final int GRPC_PORT = 9090; // Same as main app (MUST stop main app before running tests)
    private static final String ALICE_USERNAME = "alice";
    private static final String ALICE_PASSWORD = "password123";
    private static final String BOB_USERNAME = "bob";
    private static final String BOB_PASSWORD = "password123";
    
    private ManagedChannel channel;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    
    // Test context - shared between test methods
    private static String aliceToken;
    private static String bobToken;
    private static String aliceUserId;
    private static String bobUserId;
    private static String conversationId;
    private static String messageId;
    
    @BeforeEach
    void setUp() {
        channel = ManagedChannelBuilder.forAddress("localhost", GRPC_PORT)
                .usePlaintext()
                .build();
        
        restTemplate = new RestTemplate();
        objectMapper = new ObjectMapper();
    }
    
    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("1. Login Alice e Bob via REST API")
    void testLogin() {
        System.out.println("\n=== TESTE 1: LOGIN ===");
        
        // Login Alice
        LoginResponse aliceResponse = login(ALICE_USERNAME, ALICE_PASSWORD);
        assertNotNull(aliceResponse.getToken(), "Alice token deve existir");
        assertNotNull(aliceResponse.getUser().getUserId(), "Alice userId deve existir");
        
        aliceToken = aliceResponse.getToken();
        aliceUserId = aliceResponse.getUser().getUserId();
        
        System.out.println("✓ Alice autenticada:");
        System.out.println("  Token: " + aliceToken.substring(0, 20) + "...");
        System.out.println("  UserId: " + aliceUserId);
        
        // Login Bob
        LoginResponse bobResponse = login(BOB_USERNAME, BOB_PASSWORD);
        assertNotNull(bobResponse.getToken(), "Bob token deve existir");
        assertNotNull(bobResponse.getUser().getUserId(), "Bob userId deve existir");
        
        bobToken = bobResponse.getToken();
        bobUserId = bobResponse.getUser().getUserId();
        
        System.out.println("✓ Bob autenticado:");
        System.out.println("  Token: " + bobToken.substring(0, 20) + "...");
        System.out.println("  UserId: " + bobUserId);
    }
    
    @Test
    @Order(2)
    @DisplayName("2. Criar conversa entre Alice e Bob")
    void testCreateConversation() {
        System.out.println("\n=== TESTE 2: CREATE CONVERSATION ===");
        
        ConversationServiceGrpc.ConversationServiceBlockingStub stub = 
            createConversationStub(aliceToken);
        
        CreateConversationRequest request = CreateConversationRequest.newBuilder()
                .setType(ConversationType.PRIVATE)
                .addAllParticipantIds(Arrays.asList(aliceUserId, bobUserId))
                .build();
        
        CreateConversationResponse response = stub.createConversation(request);
        
        assertNotNull(response.getConversationId(), "Conversation ID deve existir");
        assertEquals(ConversationType.PRIVATE, response.getType(), "Tipo deve ser PRIVATE");
        assertTrue(response.getParticipantIdsList().contains(aliceUserId), "Alice deve ser participante");
        assertTrue(response.getParticipantIdsList().contains(bobUserId), "Bob deve ser participante");
        
        conversationId = response.getConversationId();
        
        System.out.println("✓ Conversa criada:");
        System.out.println("  ID: " + conversationId);
        System.out.println("  Tipo: " + response.getType());
        System.out.println("  Participantes: " + response.getParticipantIdsList());
    }
    
    @Test
    @Order(3)
    @DisplayName("3. Enviar mensagem (Alice → Bob)")
    void testSendMessage() {
        System.out.println("\n=== TESTE 3: SEND MESSAGE ===");
        
        ChatServiceGrpc.ChatServiceBlockingStub stub = createChatStub(aliceToken);
        
        // Note: message_id is now generated by server
        SendMessageRequest request = SendMessageRequest.newBuilder()
                .setConversationId(conversationId)
                .setSenderId(aliceUserId)
                .setRecipientId(bobUserId)
                .setMessageText("Mensagem E2E Test - Teste completo de envio!")
                .build();
        
        SendMessageResponse response = stub.sendMessage(request);
        
        // Extract messageId from server response
        messageId = response.getMessageId();
        
        assertNotNull(messageId, "Message ID deve ser gerado pelo servidor");
        assertEquals(MessageStatus.SENT, response.getStatus(), "Status inicial deve ser SENT");
        assertNotNull(response.getTimestamp(), "Timestamp deve existir");
        assertTrue(response.getSequenceNumber() > 0, "Sequence number deve ser positivo");
        
        System.out.println("✓ Mensagem enviada:");
        System.out.println("  ID (gerado pelo servidor): " + response.getMessageId());
        System.out.println("  Status: " + response.getStatus());
        System.out.println("  Sequence: " + response.getSequenceNumber());
        System.out.println("  Timestamp: " + response.getTimestamp());
    }
    
    @Test
    @Order(4)
    @DisplayName("4. Aguardar processamento Kafka (5s)")
    void testWaitForKafkaProcessing() throws InterruptedException {
        System.out.println("\n=== TESTE 4: KAFKA PROCESSING ===");
        System.out.println("Aguardando 5 segundos para:");
        System.out.println("  - MessageDeliveryWorker consumir evento");
        System.out.println("  - Persistir mensagem no MongoDB");
        System.out.println("  - Atualizar status para DELIVERED");
        
        Thread.sleep(5000);
        
        System.out.println("✓ Processamento concluído (esperado)");
    }
    
    @Test
    @Order(5)
    @DisplayName("5. Verificar status da mensagem (deve ser DELIVERED)")
    void testGetMessageStatusDelivered() {
        System.out.println("\n=== TESTE 5: MESSAGE STATUS (DELIVERED) ===");
        
        ChatServiceGrpc.ChatServiceBlockingStub stub = createChatStub(bobToken);
        
        GetMessageStatusRequest request = GetMessageStatusRequest.newBuilder()
                .setMessageId(messageId)
                .build();
        
        GetMessageStatusResponse response = stub.getMessageStatus(request);
        
        assertEquals(messageId, response.getMessageId(), "Message ID deve corresponder");
        assertEquals(MessageStatus.DELIVERED, response.getCurrentStatus(), 
                    "Status deve ser DELIVERED após processamento Kafka");
        assertTrue(response.getStateHistoryCount() >= 2, 
                  "Histórico deve ter ao menos 2 estados: SENT e DELIVERED");
        
        System.out.println("✓ Status verificado:");
        System.out.println("  Status atual: " + response.getCurrentStatus());
        System.out.println("  Histórico de estados:");
        response.getStateHistoryList().forEach(state -> {
            System.out.println("    - " + state.getState() + " em " + state.getTimestamp());
        });
    }
    
    @Test
    @Order(6)
    @DisplayName("6. Marcar mensagem como lida (Bob)")
    void testMarkMessageAsRead() {
        System.out.println("\n=== TESTE 6: MARK AS READ ===");
        
        ChatServiceGrpc.ChatServiceBlockingStub stub = createChatStub(bobToken);
        
        MarkMessageAsReadRequest request = MarkMessageAsReadRequest.newBuilder()
                .setMessageId(messageId)
                .setUserId(bobUserId)
                .build();
        
        MarkMessageAsReadResponse response = stub.markMessageAsRead(request);
        
        assertEquals(messageId, response.getMessageId(), "Message ID deve corresponder");
        assertEquals(MessageStatus.READ, response.getStatus(), "Status deve ser READ");
        assertNotNull(response.getTimestamp(), "Timestamp deve existir");
        
        System.out.println("✓ Mensagem marcada como lida:");
        System.out.println("  ID: " + response.getMessageId());
        System.out.println("  Status: " + response.getStatus());
        System.out.println("  Timestamp: " + response.getTimestamp());
    }
    
    @Test
    @Order(7)
    @DisplayName("7. Verificar estado final (SENT → DELIVERED → READ)")
    void testGetMessageStatusFinal() {
        System.out.println("\n=== TESTE 7: ESTADO FINAL ===");
        
        ChatServiceGrpc.ChatServiceBlockingStub stub = createChatStub(bobToken);
        
        GetMessageStatusRequest request = GetMessageStatusRequest.newBuilder()
                .setMessageId(messageId)
                .build();
        
        GetMessageStatusResponse response = stub.getMessageStatus(request);
        
        assertEquals(MessageStatus.READ, response.getCurrentStatus(), 
                    "Status final deve ser READ");
        assertEquals(3, response.getStateHistoryCount(), 
                    "Histórico deve ter 3 estados: SENT → DELIVERED → READ");
        
        // Validar ordem dos estados
        assertEquals(MessageStatus.SENT, response.getStateHistory(0).getState());
        assertEquals(MessageStatus.DELIVERED, response.getStateHistory(1).getState());
        assertEquals(MessageStatus.READ, response.getStateHistory(2).getState());
        
        System.out.println("✓ Ciclo de vida completo:");
        System.out.println("  Status final: " + response.getCurrentStatus());
        System.out.println("  Transições:");
        response.getStateHistoryList().forEach(state -> {
            System.out.println("    " + state.getState() + " → " + state.getTimestamp());
        });
    }
    
    @Test
    @Order(8)
    @DisplayName("8. Verificar persistência no histórico da conversa")
    void testGetConversationHistory() {
        System.out.println("\n=== TESTE 8: CONVERSATION HISTORY ===");
        
        ConversationServiceGrpc.ConversationServiceBlockingStub stub = 
            createConversationStub(bobToken);
        
        GetConversationHistoryRequest request = GetConversationHistoryRequest.newBuilder()
                .setConversationId(conversationId)
                .setLimit(10)
                .build();
        
        GetConversationHistoryResponse response = stub.getConversationHistory(request);
        
        assertTrue(response.getMessagesCount() > 0, "Deve ter ao menos 1 mensagem");
        
        // Encontrar nossa mensagem de teste
        Message testMessage = response.getMessagesList().stream()
                .filter(m -> m.getMessageId().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Mensagem de teste não encontrada no histórico"));
        
        assertEquals("Mensagem E2E Test - Teste completo de envio!", 
                    testMessage.getMessageText(), "Texto da mensagem deve corresponder");
        assertEquals(MessageStatus.READ, testMessage.getCurrentStatus(), 
                    "Status deve ser READ no histórico");
        assertEquals(aliceUserId, testMessage.getSender().getUserId(), 
                    "Sender deve ser Alice");
        
        System.out.println("✓ Mensagem encontrada no histórico:");
        System.out.println("  ID: " + testMessage.getMessageId());
        System.out.println("  Texto: " + testMessage.getMessageText());
        System.out.println("  Status: " + testMessage.getCurrentStatus());
        System.out.println("  Sender: " + testMessage.getSender().getUsername());
        System.out.println("  Timestamp: " + testMessage.getTimestamp());
    }
    
    // ========== Helper Methods ==========
    
    private LoginResponse login(String username, String password) {
        String url = "http://localhost:" + restPort + "/api/auth/login";
        
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        
        return restTemplate.postForObject(url, request, LoginResponse.class);
    }
    
    private ConversationServiceGrpc.ConversationServiceBlockingStub createConversationStub(String token) {
        Metadata metadata = new Metadata();
        metadata.put(
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
            "Bearer " + token
        );
        
        return ConversationServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }
    
    private ChatServiceGrpc.ChatServiceBlockingStub createChatStub(String token) {
        Metadata metadata = new Metadata();
        metadata.put(
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
            "Bearer " + token
        );
        
        return ChatServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }
}
