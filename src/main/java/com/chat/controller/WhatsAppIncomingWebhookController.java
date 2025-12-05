package com.chat.controller;

import com.chat.dto.webhook.WhatsAppWebhookDto;
import com.chat.service.WhatsAppWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * Controller para receber webhooks de mensagens RECEBIDAS do WhatsApp Business API.
 * 
 * Endpoints:
 * - POST /api/webhooks/whatsapp/incoming - Recebe webhooks de mensagens
 * - GET /api/webhooks/whatsapp/incoming - Verificação de webhook (Meta requer)
 * 
 * Para STATUS de mensagens ENVIADAS (delivered/read), use WebhookController.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/whatsapp/incoming")
@RequiredArgsConstructor
public class WhatsAppIncomingWebhookController {

    private final WhatsAppWebhookService whatsAppWebhookService;

    /**
     * Endpoint de verificação do webhook (Meta requer durante configuração).
     * 
     * Quando você configura o webhook no Meta Business Suite, a Meta envia um GET
     * com os parâmetros hub.mode, hub.challenge e hub.verify_token.
     * 
     * Você deve:
     * 1. Verificar se hub.verify_token corresponde ao seu token configurado
     * 2. Retornar hub.challenge no corpo da resposta
     * 
     * Exemplo de requisição:
     * GET /api/webhooks/whatsapp/incoming?hub.mode=subscribe&hub.challenge=1234567890&hub.verify_token=meu-token-secreto
     * 
     * @param mode Modo, deve ser "subscribe"
     * @param challenge Desafio para ecoar de volta
     * @param verifyToken Token de verificação configurado no Meta Business Suite
     * @return O challenge se verificação OK, 403 caso contrário
     */
    @GetMapping
    public ResponseEntity<?> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.challenge") String challenge,
            @RequestParam("hub.verify_token") String verifyToken) {

        log.info("Webhook verification request - mode: {}, verifyToken: {}", mode, verifyToken);

        // Verificar se o modo é "subscribe"
        if (!"subscribe".equals(mode)) {
            log.warn("Invalid hub.mode: {}", mode);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid mode");
        }

        // Verificar se o token corresponde ao configurado
        // TODO: Buscar do application.yml (webhook.whatsapp.verify-token)
        String expectedToken = "whatsapp-verify-token-12345";
        if (!expectedToken.equals(verifyToken)) {
            log.warn("Invalid verify token. Expected: {}, Got: {}", expectedToken, verifyToken);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid verify token");
        }

        // Retornar o challenge
        log.info("Webhook verification successful, returning challenge");
        return ResponseEntity.ok(challenge);
    }

    /**
     * Recebe webhooks do WhatsApp Business API.
     * 
     * A Meta envia notificações para este endpoint quando:
     * - Uma mensagem é recebida de um usuário
     * - Uma mensagem enviada muda de status (entregue/lida)
     * 
     * Este controller trata apenas MENSAGENS RECEBIDAS.
     * Para status de mensagens enviadas, use o endpoint /api/webhooks/whatsapp (WebhookController).
     * 
     * Validação de assinatura:
     * - Header: X-Hub-Signature-256
     * - Formato: sha256=<hash>
     * - Hash: HMAC SHA-256 do corpo usando secret key
     * 
     * @param signature Header X-Hub-Signature-256
     * @param payload Corpo do webhook
     * @return 200 OK se processado com sucesso
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> handleIncomingWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload) {

        log.info("Received WhatsApp incoming webhook - signature: {}", signature);
        log.debug("Webhook payload: {}", payload);

        // Validar assinatura HMAC (apenas se configurada)
        if (signature != null && !signature.isEmpty()) {
            if (!validateSignature(signature, payload)) {
                log.error("Invalid webhook signature");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Invalid signature"));
            }
            log.debug("Webhook signature validated successfully");
        } else {
            log.warn("No signature provided - skipping validation (OK for mocks)");
        }

        try {
            // Processar webhook
            whatsAppWebhookService.processIncomingWebhook(payload);
            
            log.info("WhatsApp incoming webhook processed successfully");
            return ResponseEntity.ok(Map.of("status", "success"));

        } catch (Exception e) {
            log.error("Error processing WhatsApp incoming webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process webhook: " + e.getMessage()));
        }
    }

    /**
     * Valida a assinatura HMAC SHA-256 do webhook.
     * 
     * A Meta assina todos os webhooks com HMAC SHA-256 usando seu app secret.
     * 
     * Processo:
     * 1. Extrair hash do header (formato: "sha256=abc123...")
     * 2. Calcular HMAC SHA-256 do corpo usando secret key
     * 3. Comparar hashes
     * 
     * @param signatureHeader Header X-Hub-Signature-256
     * @param payload Corpo da requisição
     * @return true se assinatura válida
     */
    private boolean validateSignature(String signatureHeader, String payload) {
        try {
            // Extrair hash do header (formato: "sha256=<hash>")
            if (!signatureHeader.startsWith("sha256=")) {
                log.warn("Invalid signature format: {}", signatureHeader);
                return false;
            }
            String expectedHash = signatureHeader.substring(7); // Remove "sha256="

            // Buscar secret key da configuração
            // TODO: Buscar do application.yml (webhook.whatsapp.secret-key)
            String secretKey = "whatsapp-test-secret-key-12345";

            // Calcular HMAC SHA-256
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            // Converter para hex string
            String calculatedHash = HexFormat.of().formatHex(hash);

            // Comparar hashes (case-insensitive)
            boolean isValid = calculatedHash.equalsIgnoreCase(expectedHash);
            
            if (!isValid) {
                log.warn("Signature mismatch - Expected: {}, Calculated: {}", expectedHash, calculatedHash);
            }
            
            return isValid;

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error validating webhook signature", e);
            return false;
        }
    }
}
