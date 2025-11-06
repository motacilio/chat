package br.com.meuprojeto.chat.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.64.0)",
    comments = "Source: chat_api.v1.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ChatFrontendServiceGrpc {

  private ChatFrontendServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "chat_api.v1.ChatFrontendService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.SendTextMessageRequest,
      br.com.meuprojeto.chat.v1.SendTextMessageResponse> getSendTextMessageMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SendTextMessage",
      requestType = br.com.meuprojeto.chat.v1.SendTextMessageRequest.class,
      responseType = br.com.meuprojeto.chat.v1.SendTextMessageResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.SendTextMessageRequest,
      br.com.meuprojeto.chat.v1.SendTextMessageResponse> getSendTextMessageMethod() {
    io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.SendTextMessageRequest, br.com.meuprojeto.chat.v1.SendTextMessageResponse> getSendTextMessageMethod;
    if ((getSendTextMessageMethod = ChatFrontendServiceGrpc.getSendTextMessageMethod) == null) {
      synchronized (ChatFrontendServiceGrpc.class) {
        if ((getSendTextMessageMethod = ChatFrontendServiceGrpc.getSendTextMessageMethod) == null) {
          ChatFrontendServiceGrpc.getSendTextMessageMethod = getSendTextMessageMethod =
              io.grpc.MethodDescriptor.<br.com.meuprojeto.chat.v1.SendTextMessageRequest, br.com.meuprojeto.chat.v1.SendTextMessageResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SendTextMessage"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  br.com.meuprojeto.chat.v1.SendTextMessageRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  br.com.meuprojeto.chat.v1.SendTextMessageResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ChatFrontendServiceMethodDescriptorSupplier("SendTextMessage"))
              .build();
        }
      }
    }
    return getSendTextMessageMethod;
  }

  private static volatile io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.PrepareMediaUploadRequest,
      br.com.meuprojeto.chat.v1.PrepareMediaUploadResponse> getPrepareMediaUploadMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "PrepareMediaUpload",
      requestType = br.com.meuprojeto.chat.v1.PrepareMediaUploadRequest.class,
      responseType = br.com.meuprojeto.chat.v1.PrepareMediaUploadResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.PrepareMediaUploadRequest,
      br.com.meuprojeto.chat.v1.PrepareMediaUploadResponse> getPrepareMediaUploadMethod() {
    io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.PrepareMediaUploadRequest, br.com.meuprojeto.chat.v1.PrepareMediaUploadResponse> getPrepareMediaUploadMethod;
    if ((getPrepareMediaUploadMethod = ChatFrontendServiceGrpc.getPrepareMediaUploadMethod) == null) {
      synchronized (ChatFrontendServiceGrpc.class) {
        if ((getPrepareMediaUploadMethod = ChatFrontendServiceGrpc.getPrepareMediaUploadMethod) == null) {
          ChatFrontendServiceGrpc.getPrepareMediaUploadMethod = getPrepareMediaUploadMethod =
              io.grpc.MethodDescriptor.<br.com.meuprojeto.chat.v1.PrepareMediaUploadRequest, br.com.meuprojeto.chat.v1.PrepareMediaUploadResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "PrepareMediaUpload"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  br.com.meuprojeto.chat.v1.PrepareMediaUploadRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  br.com.meuprojeto.chat.v1.PrepareMediaUploadResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ChatFrontendServiceMethodDescriptorSupplier("PrepareMediaUpload"))
              .build();
        }
      }
    }
    return getPrepareMediaUploadMethod;
  }

  private static volatile io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.CompleteMediaUploadRequest,
      br.com.meuprojeto.chat.v1.CompleteMediaUploadResponse> getCompleteMediaUploadMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CompleteMediaUpload",
      requestType = br.com.meuprojeto.chat.v1.CompleteMediaUploadRequest.class,
      responseType = br.com.meuprojeto.chat.v1.CompleteMediaUploadResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.CompleteMediaUploadRequest,
      br.com.meuprojeto.chat.v1.CompleteMediaUploadResponse> getCompleteMediaUploadMethod() {
    io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.CompleteMediaUploadRequest, br.com.meuprojeto.chat.v1.CompleteMediaUploadResponse> getCompleteMediaUploadMethod;
    if ((getCompleteMediaUploadMethod = ChatFrontendServiceGrpc.getCompleteMediaUploadMethod) == null) {
      synchronized (ChatFrontendServiceGrpc.class) {
        if ((getCompleteMediaUploadMethod = ChatFrontendServiceGrpc.getCompleteMediaUploadMethod) == null) {
          ChatFrontendServiceGrpc.getCompleteMediaUploadMethod = getCompleteMediaUploadMethod =
              io.grpc.MethodDescriptor.<br.com.meuprojeto.chat.v1.CompleteMediaUploadRequest, br.com.meuprojeto.chat.v1.CompleteMediaUploadResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CompleteMediaUpload"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  br.com.meuprojeto.chat.v1.CompleteMediaUploadRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  br.com.meuprojeto.chat.v1.CompleteMediaUploadResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ChatFrontendServiceMethodDescriptorSupplier("CompleteMediaUpload"))
              .build();
        }
      }
    }
    return getCompleteMediaUploadMethod;
  }

  private static volatile io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.GetMessagesRequest,
      br.com.meuprojeto.chat.v1.GetMessagesResponse> getGetMessagesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetMessages",
      requestType = br.com.meuprojeto.chat.v1.GetMessagesRequest.class,
      responseType = br.com.meuprojeto.chat.v1.GetMessagesResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.GetMessagesRequest,
      br.com.meuprojeto.chat.v1.GetMessagesResponse> getGetMessagesMethod() {
    io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.GetMessagesRequest, br.com.meuprojeto.chat.v1.GetMessagesResponse> getGetMessagesMethod;
    if ((getGetMessagesMethod = ChatFrontendServiceGrpc.getGetMessagesMethod) == null) {
      synchronized (ChatFrontendServiceGrpc.class) {
        if ((getGetMessagesMethod = ChatFrontendServiceGrpc.getGetMessagesMethod) == null) {
          ChatFrontendServiceGrpc.getGetMessagesMethod = getGetMessagesMethod =
              io.grpc.MethodDescriptor.<br.com.meuprojeto.chat.v1.GetMessagesRequest, br.com.meuprojeto.chat.v1.GetMessagesResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetMessages"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  br.com.meuprojeto.chat.v1.GetMessagesRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  br.com.meuprojeto.chat.v1.GetMessagesResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ChatFrontendServiceMethodDescriptorSupplier("GetMessages"))
              .build();
        }
      }
    }
    return getGetMessagesMethod;
  }

  private static volatile io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.GetConversationsRequest,
      br.com.meuprojeto.chat.v1.GetConversationsResponse> getGetConversationsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetConversations",
      requestType = br.com.meuprojeto.chat.v1.GetConversationsRequest.class,
      responseType = br.com.meuprojeto.chat.v1.GetConversationsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.GetConversationsRequest,
      br.com.meuprojeto.chat.v1.GetConversationsResponse> getGetConversationsMethod() {
    io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.GetConversationsRequest, br.com.meuprojeto.chat.v1.GetConversationsResponse> getGetConversationsMethod;
    if ((getGetConversationsMethod = ChatFrontendServiceGrpc.getGetConversationsMethod) == null) {
      synchronized (ChatFrontendServiceGrpc.class) {
        if ((getGetConversationsMethod = ChatFrontendServiceGrpc.getGetConversationsMethod) == null) {
          ChatFrontendServiceGrpc.getGetConversationsMethod = getGetConversationsMethod =
              io.grpc.MethodDescriptor.<br.com.meuprojeto.chat.v1.GetConversationsRequest, br.com.meuprojeto.chat.v1.GetConversationsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetConversations"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  br.com.meuprojeto.chat.v1.GetConversationsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  br.com.meuprojeto.chat.v1.GetConversationsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ChatFrontendServiceMethodDescriptorSupplier("GetConversations"))
              .build();
        }
      }
    }
    return getGetConversationsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.SubscribeToEventsRequest,
      br.com.meuprojeto.chat.v1.ServerEvent> getSubscribeToEventsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SubscribeToEvents",
      requestType = br.com.meuprojeto.chat.v1.SubscribeToEventsRequest.class,
      responseType = br.com.meuprojeto.chat.v1.ServerEvent.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.SubscribeToEventsRequest,
      br.com.meuprojeto.chat.v1.ServerEvent> getSubscribeToEventsMethod() {
    io.grpc.MethodDescriptor<br.com.meuprojeto.chat.v1.SubscribeToEventsRequest, br.com.meuprojeto.chat.v1.ServerEvent> getSubscribeToEventsMethod;
    if ((getSubscribeToEventsMethod = ChatFrontendServiceGrpc.getSubscribeToEventsMethod) == null) {
      synchronized (ChatFrontendServiceGrpc.class) {
        if ((getSubscribeToEventsMethod = ChatFrontendServiceGrpc.getSubscribeToEventsMethod) == null) {
          ChatFrontendServiceGrpc.getSubscribeToEventsMethod = getSubscribeToEventsMethod =
              io.grpc.MethodDescriptor.<br.com.meuprojeto.chat.v1.SubscribeToEventsRequest, br.com.meuprojeto.chat.v1.ServerEvent>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SubscribeToEvents"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  br.com.meuprojeto.chat.v1.SubscribeToEventsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  br.com.meuprojeto.chat.v1.ServerEvent.getDefaultInstance()))
              .setSchemaDescriptor(new ChatFrontendServiceMethodDescriptorSupplier("SubscribeToEvents"))
              .build();
        }
      }
    }
    return getSubscribeToEventsMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ChatFrontendServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ChatFrontendServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ChatFrontendServiceStub>() {
        @java.lang.Override
        public ChatFrontendServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ChatFrontendServiceStub(channel, callOptions);
        }
      };
    return ChatFrontendServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ChatFrontendServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ChatFrontendServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ChatFrontendServiceBlockingStub>() {
        @java.lang.Override
        public ChatFrontendServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ChatFrontendServiceBlockingStub(channel, callOptions);
        }
      };
    return ChatFrontendServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ChatFrontendServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ChatFrontendServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ChatFrontendServiceFutureStub>() {
        @java.lang.Override
        public ChatFrontendServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ChatFrontendServiceFutureStub(channel, callOptions);
        }
      };
    return ChatFrontendServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     * <pre>
     * === FLUXO 1: MENSAGEM DE TEXTO ===
     * Cliente envia um payload de texto. API enfileira no Broker e responde.
     * </pre>
     */
    default void sendTextMessage(br.com.meuprojeto.chat.v1.SendTextMessageRequest request,
        io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.SendTextMessageResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSendTextMessageMethod(), responseObserver);
    }

    /**
     * <pre>
     * === FLUXO 2: ARQUIVO (FASE 1 - INÍCIO) ===
     * Cliente anuncia o upload de mídia e envia metadados.
     * API gera file_id, salva como "Pendente" e retorna URLs pré-assinadas.
     * </pre>
     */
    default void prepareMediaUpload(br.com.meuprojeto.chat.v1.PrepareMediaUploadRequest request,
        io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.PrepareMediaUploadResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getPrepareMediaUploadMethod(), responseObserver);
    }

    /**
     * <pre>
     * === FLUXO 2: ARQUIVO (FASE 2 - CONCLUSÃO) ===
     * Cliente notifica que terminou o upload direto para o Object Storage.
     * API valida, atualiza status para "Completo" e publica no Broker.
     * </pre>
     */
    default void completeMediaUpload(br.com.meuprojeto.chat.v1.CompleteMediaUploadRequest request,
        io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.CompleteMediaUploadResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCompleteMediaUploadMethod(), responseObserver);
    }

    /**
     * <pre>
     * === FLUXO DE LEITURA (HISTÓRICO) ===
     * Cliente busca o histórico de mensagens de uma conversa (paginado).
     * </pre>
     */
    default void getMessages(br.com.meuprojeto.chat.v1.GetMessagesRequest request,
        io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.GetMessagesResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetMessagesMethod(), responseObserver);
    }

    /**
     * <pre>
     * === FLUXO DE LEITURA (LISTA DE CHATS) ===
     * Cliente busca sua lista de conversas (paginado).
     * </pre>
     */
    default void getConversations(br.com.meuprojeto.chat.v1.GetConversationsRequest request,
        io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.GetConversationsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetConversationsMethod(), responseObserver);
    }

    /**
     * <pre>
     * === FLUXO DE TEMPO REAL (RECEBIMENTO) ===
     * Cliente se conecta para receber eventos em tempo real (novas mensagens,
     * status, etc.) que vêm do "Notification / Push Service".
     * </pre>
     */
    default void subscribeToEvents(br.com.meuprojeto.chat.v1.SubscribeToEventsRequest request,
        io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.ServerEvent> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSubscribeToEventsMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ChatFrontendService.
   */
  public static abstract class ChatFrontendServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ChatFrontendServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ChatFrontendService.
   */
  public static final class ChatFrontendServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ChatFrontendServiceStub> {
    private ChatFrontendServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ChatFrontendServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ChatFrontendServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * === FLUXO 1: MENSAGEM DE TEXTO ===
     * Cliente envia um payload de texto. API enfileira no Broker e responde.
     * </pre>
     */
    public void sendTextMessage(br.com.meuprojeto.chat.v1.SendTextMessageRequest request,
        io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.SendTextMessageResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSendTextMessageMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * === FLUXO 2: ARQUIVO (FASE 1 - INÍCIO) ===
     * Cliente anuncia o upload de mídia e envia metadados.
     * API gera file_id, salva como "Pendente" e retorna URLs pré-assinadas.
     * </pre>
     */
    public void prepareMediaUpload(br.com.meuprojeto.chat.v1.PrepareMediaUploadRequest request,
        io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.PrepareMediaUploadResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getPrepareMediaUploadMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * === FLUXO 2: ARQUIVO (FASE 2 - CONCLUSÃO) ===
     * Cliente notifica que terminou o upload direto para o Object Storage.
     * API valida, atualiza status para "Completo" e publica no Broker.
     * </pre>
     */
    public void completeMediaUpload(br.com.meuprojeto.chat.v1.CompleteMediaUploadRequest request,
        io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.CompleteMediaUploadResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCompleteMediaUploadMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * === FLUXO DE LEITURA (HISTÓRICO) ===
     * Cliente busca o histórico de mensagens de uma conversa (paginado).
     * </pre>
     */
    public void getMessages(br.com.meuprojeto.chat.v1.GetMessagesRequest request,
        io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.GetMessagesResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetMessagesMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * === FLUXO DE LEITURA (LISTA DE CHATS) ===
     * Cliente busca sua lista de conversas (paginado).
     * </pre>
     */
    public void getConversations(br.com.meuprojeto.chat.v1.GetConversationsRequest request,
        io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.GetConversationsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetConversationsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * === FLUXO DE TEMPO REAL (RECEBIMENTO) ===
     * Cliente se conecta para receber eventos em tempo real (novas mensagens,
     * status, etc.) que vêm do "Notification / Push Service".
     * </pre>
     */
    public void subscribeToEvents(br.com.meuprojeto.chat.v1.SubscribeToEventsRequest request,
        io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.ServerEvent> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getSubscribeToEventsMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ChatFrontendService.
   */
  public static final class ChatFrontendServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ChatFrontendServiceBlockingStub> {
    private ChatFrontendServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ChatFrontendServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ChatFrontendServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * === FLUXO 1: MENSAGEM DE TEXTO ===
     * Cliente envia um payload de texto. API enfileira no Broker e responde.
     * </pre>
     */
    public br.com.meuprojeto.chat.v1.SendTextMessageResponse sendTextMessage(br.com.meuprojeto.chat.v1.SendTextMessageRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSendTextMessageMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * === FLUXO 2: ARQUIVO (FASE 1 - INÍCIO) ===
     * Cliente anuncia o upload de mídia e envia metadados.
     * API gera file_id, salva como "Pendente" e retorna URLs pré-assinadas.
     * </pre>
     */
    public br.com.meuprojeto.chat.v1.PrepareMediaUploadResponse prepareMediaUpload(br.com.meuprojeto.chat.v1.PrepareMediaUploadRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getPrepareMediaUploadMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * === FLUXO 2: ARQUIVO (FASE 2 - CONCLUSÃO) ===
     * Cliente notifica que terminou o upload direto para o Object Storage.
     * API valida, atualiza status para "Completo" e publica no Broker.
     * </pre>
     */
    public br.com.meuprojeto.chat.v1.CompleteMediaUploadResponse completeMediaUpload(br.com.meuprojeto.chat.v1.CompleteMediaUploadRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCompleteMediaUploadMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * === FLUXO DE LEITURA (HISTÓRICO) ===
     * Cliente busca o histórico de mensagens de uma conversa (paginado).
     * </pre>
     */
    public br.com.meuprojeto.chat.v1.GetMessagesResponse getMessages(br.com.meuprojeto.chat.v1.GetMessagesRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetMessagesMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * === FLUXO DE LEITURA (LISTA DE CHATS) ===
     * Cliente busca sua lista de conversas (paginado).
     * </pre>
     */
    public br.com.meuprojeto.chat.v1.GetConversationsResponse getConversations(br.com.meuprojeto.chat.v1.GetConversationsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetConversationsMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * === FLUXO DE TEMPO REAL (RECEBIMENTO) ===
     * Cliente se conecta para receber eventos em tempo real (novas mensagens,
     * status, etc.) que vêm do "Notification / Push Service".
     * </pre>
     */
    public java.util.Iterator<br.com.meuprojeto.chat.v1.ServerEvent> subscribeToEvents(
        br.com.meuprojeto.chat.v1.SubscribeToEventsRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getSubscribeToEventsMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ChatFrontendService.
   */
  public static final class ChatFrontendServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ChatFrontendServiceFutureStub> {
    private ChatFrontendServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ChatFrontendServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ChatFrontendServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * === FLUXO 1: MENSAGEM DE TEXTO ===
     * Cliente envia um payload de texto. API enfileira no Broker e responde.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<br.com.meuprojeto.chat.v1.SendTextMessageResponse> sendTextMessage(
        br.com.meuprojeto.chat.v1.SendTextMessageRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSendTextMessageMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * === FLUXO 2: ARQUIVO (FASE 1 - INÍCIO) ===
     * Cliente anuncia o upload de mídia e envia metadados.
     * API gera file_id, salva como "Pendente" e retorna URLs pré-assinadas.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<br.com.meuprojeto.chat.v1.PrepareMediaUploadResponse> prepareMediaUpload(
        br.com.meuprojeto.chat.v1.PrepareMediaUploadRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getPrepareMediaUploadMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * === FLUXO 2: ARQUIVO (FASE 2 - CONCLUSÃO) ===
     * Cliente notifica que terminou o upload direto para o Object Storage.
     * API valida, atualiza status para "Completo" e publica no Broker.
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<br.com.meuprojeto.chat.v1.CompleteMediaUploadResponse> completeMediaUpload(
        br.com.meuprojeto.chat.v1.CompleteMediaUploadRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCompleteMediaUploadMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * === FLUXO DE LEITURA (HISTÓRICO) ===
     * Cliente busca o histórico de mensagens de uma conversa (paginado).
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<br.com.meuprojeto.chat.v1.GetMessagesResponse> getMessages(
        br.com.meuprojeto.chat.v1.GetMessagesRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetMessagesMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * === FLUXO DE LEITURA (LISTA DE CHATS) ===
     * Cliente busca sua lista de conversas (paginado).
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<br.com.meuprojeto.chat.v1.GetConversationsResponse> getConversations(
        br.com.meuprojeto.chat.v1.GetConversationsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetConversationsMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SEND_TEXT_MESSAGE = 0;
  private static final int METHODID_PREPARE_MEDIA_UPLOAD = 1;
  private static final int METHODID_COMPLETE_MEDIA_UPLOAD = 2;
  private static final int METHODID_GET_MESSAGES = 3;
  private static final int METHODID_GET_CONVERSATIONS = 4;
  private static final int METHODID_SUBSCRIBE_TO_EVENTS = 5;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SEND_TEXT_MESSAGE:
          serviceImpl.sendTextMessage((br.com.meuprojeto.chat.v1.SendTextMessageRequest) request,
              (io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.SendTextMessageResponse>) responseObserver);
          break;
        case METHODID_PREPARE_MEDIA_UPLOAD:
          serviceImpl.prepareMediaUpload((br.com.meuprojeto.chat.v1.PrepareMediaUploadRequest) request,
              (io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.PrepareMediaUploadResponse>) responseObserver);
          break;
        case METHODID_COMPLETE_MEDIA_UPLOAD:
          serviceImpl.completeMediaUpload((br.com.meuprojeto.chat.v1.CompleteMediaUploadRequest) request,
              (io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.CompleteMediaUploadResponse>) responseObserver);
          break;
        case METHODID_GET_MESSAGES:
          serviceImpl.getMessages((br.com.meuprojeto.chat.v1.GetMessagesRequest) request,
              (io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.GetMessagesResponse>) responseObserver);
          break;
        case METHODID_GET_CONVERSATIONS:
          serviceImpl.getConversations((br.com.meuprojeto.chat.v1.GetConversationsRequest) request,
              (io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.GetConversationsResponse>) responseObserver);
          break;
        case METHODID_SUBSCRIBE_TO_EVENTS:
          serviceImpl.subscribeToEvents((br.com.meuprojeto.chat.v1.SubscribeToEventsRequest) request,
              (io.grpc.stub.StreamObserver<br.com.meuprojeto.chat.v1.ServerEvent>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getSendTextMessageMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              br.com.meuprojeto.chat.v1.SendTextMessageRequest,
              br.com.meuprojeto.chat.v1.SendTextMessageResponse>(
                service, METHODID_SEND_TEXT_MESSAGE)))
        .addMethod(
          getPrepareMediaUploadMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              br.com.meuprojeto.chat.v1.PrepareMediaUploadRequest,
              br.com.meuprojeto.chat.v1.PrepareMediaUploadResponse>(
                service, METHODID_PREPARE_MEDIA_UPLOAD)))
        .addMethod(
          getCompleteMediaUploadMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              br.com.meuprojeto.chat.v1.CompleteMediaUploadRequest,
              br.com.meuprojeto.chat.v1.CompleteMediaUploadResponse>(
                service, METHODID_COMPLETE_MEDIA_UPLOAD)))
        .addMethod(
          getGetMessagesMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              br.com.meuprojeto.chat.v1.GetMessagesRequest,
              br.com.meuprojeto.chat.v1.GetMessagesResponse>(
                service, METHODID_GET_MESSAGES)))
        .addMethod(
          getGetConversationsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              br.com.meuprojeto.chat.v1.GetConversationsRequest,
              br.com.meuprojeto.chat.v1.GetConversationsResponse>(
                service, METHODID_GET_CONVERSATIONS)))
        .addMethod(
          getSubscribeToEventsMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              br.com.meuprojeto.chat.v1.SubscribeToEventsRequest,
              br.com.meuprojeto.chat.v1.ServerEvent>(
                service, METHODID_SUBSCRIBE_TO_EVENTS)))
        .build();
  }

  private static abstract class ChatFrontendServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ChatFrontendServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return br.com.meuprojeto.chat.v1.ChatApiProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ChatFrontendService");
    }
  }

  private static final class ChatFrontendServiceFileDescriptorSupplier
      extends ChatFrontendServiceBaseDescriptorSupplier {
    ChatFrontendServiceFileDescriptorSupplier() {}
  }

  private static final class ChatFrontendServiceMethodDescriptorSupplier
      extends ChatFrontendServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ChatFrontendServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (ChatFrontendServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ChatFrontendServiceFileDescriptorSupplier())
              .addMethod(getSendTextMessageMethod())
              .addMethod(getPrepareMediaUploadMethod())
              .addMethod(getCompleteMediaUploadMethod())
              .addMethod(getGetMessagesMethod())
              .addMethod(getGetConversationsMethod())
              .addMethod(getSubscribeToEventsMethod())
              .build();
        }
      }
    }
    return result;
  }
}
