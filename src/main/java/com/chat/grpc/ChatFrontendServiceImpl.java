package com.chat.grpc;

import com.google.protobuf.Timestamp;

import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;

import br.com.meuprojeto.chat.v1.CompleteMediaUploadRequest;
import br.com.meuprojeto.chat.v1.CompleteMediaUploadResponse;
import br.com.meuprojeto.chat.v1.GetConversationsRequest;
import br.com.meuprojeto.chat.v1.GetConversationsResponse;
import br.com.meuprojeto.chat.v1.GetMessagesRequest;
import br.com.meuprojeto.chat.v1.GetMessagesResponse;
import br.com.meuprojeto.chat.v1.PrepareMediaUploadRequest;
import br.com.meuprojeto.chat.v1.PrepareMediaUploadResponse;
import br.com.meuprojeto.chat.v1.SendTextMessageRequest;
import br.com.meuprojeto.chat.v1.SendTextMessageResponse;
import br.com.meuprojeto.chat.v1.ServerEvent;
import br.com.meuprojeto.chat.v1.SubscribeToEventsRequest;
import br.com.meuprojeto.chat.v1.ChatFrontendServiceGrpc.ChatFrontendServiceImplBase;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

@GrpcService
public class ChatFrontendServiceImpl extends ChatFrontendServiceImplBase {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ChatFrontendServiceImpl.class);

    // TODO: INJETAR RABBITTEMPLATE do SPRING AMQP AQUI
    // @Autowired
    // private RabbitTemplate rabbittemplate;


    @Override
    public void sendTextMessage(SendTextMessageRequest request,
        StreamObserver<SendTextMessageResponse> responseObserver) {
        
        String clientId = request.getClientMessageId();
        String conversationId = request.getConversationId();
            
        
         log.info("[FRONTEND] Recebida SendTextMessage (id: {}) para conversa {}", clientId, conversationId);
         
         
        // 2. Enfileirar no Broker (PRÓXIMO PASSO)
        // Por enquanto, vamos apenas simular.
        // rabbitTemplate.convertAndSend("exchange_mensagens", "routing_key_texto", request);

        Timestamp acceptedTimestamp = Timestamp.newBuilder()
                                    .setSeconds(System.currentTimeMillis() / 1000)
                                    .build();

        SendTextMessageResponse response = SendTextMessageResponse.newBuilder()
                                        .setServerMessageId(clientId)
                                        .setAcceptedAt(acceptedTimestamp)
                                        .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    
    @Override
    public void prepareMediaUpload(PrepareMediaUploadRequest request,
            StreamObserver<PrepareMediaUploadResponse> responseObserver) {
        
        log.info("[FRONTEND] Recebida PrepareMediaUpload (id: {}) para arquivo: {}", request.getClientMessageId(), request.getFileInfo().getFileName());


        // TODO:
        // 1.CHAMAR UM SERVICO PARA GERAR URLs PRE-ASSINADAS (EX:minIO)
        // 2.SALVAR METADADOS NO MONGO COMO "PENDING" 
        

        PrepareMediaUploadResponse response = PrepareMediaUploadResponse.newBuilder()
                                              .setServerFileId("simulado-file-id-" + request.getClientMessageId())
                                              .setUploadId("simulado-upload-id-s3")
                                              .addPresignedUrls("http://minio.exemplo.com/upload-chunk-1") // MUDAR QUANDO CADASTRAR MINIO
                                              .addPresignedUrls("http://minio.exemplo.com/upload-chunk-2")
                                              .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }


    @Override
    public void completeMediaUpload(CompleteMediaUploadRequest request, StreamObserver<CompleteMediaUploadResponse> responseObserver) {
        log.info("[FRONTEND] Recebida CompleteMediaUpload (fileId: {})", request.getServerFileId());

        // TODO:
        // 1. Chamar o serviço (ex: MinIO) para finalizar o 'multipart upload'.
        // 2. Atualizar o status no Mongo para "COMPLETED".
        // 3. Publicar a *mensagem de mídia* no Broker (RabbitMQ)

        //Simulação da resposta
        Timestamp acceptedTimestamp = Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1000).build();

        CompleteMediaUploadResponse response = CompleteMediaUploadResponse.newBuilder()
            .setServerMessageId("simulado-msg-id-midia")
            .setAcceptedAt(acceptedTimestamp)
            .setFinalMediaUrl("http://minio.exemplo.com/final/" + request.getServerFileId())
            .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }


    @Override
    public void getMessages(GetMessagesRequest request, StreamObserver<GetMessagesResponse> responseObserver) {
        log.warn("GetMessages ainda não implementado");

        responseObserver.onError(Status.UNIMPLEMENTED.withDescription("GetMessages nao implementado").asRuntimeException());
    }

    @Override
    public void getConversations(GetConversationsRequest request, StreamObserver<GetConversationsResponse> responseObserver) {
        log.warn("getConversations nao implementado");

        responseObserver.onError(Status.UNIMPLEMENTED.withDescription("getConversations nao implementado").asRuntimeException());
    }

    @Override
    public void subscribeToEvents(SubscribeToEventsRequest request, StreamObserver<ServerEvent> responseObserver) {
        log.warn("subscribeToEvents nao implementado");

        responseObserver.onError(Status.UNIMPLEMENTED.withDescription("subscriboToEvents nao implementado").asRuntimeException());
    }

}