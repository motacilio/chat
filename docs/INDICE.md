# 📚 Documentação do Projeto

Índice completo da documentação técnica do Chat API.

---

## 🏗️ Arquitetura

### [ARQUITETURA.md](arquitetura/ARQUITETURA.md)
Visão geral da arquitetura do sistema, incluindo:
- Componentes principais
- Fluxo de dados
- Padrões de design utilizados
- Decisões arquiteturais

### [DESIGN-MOCKS.md](arquitetura/DESIGN-MOCKS.md)
Design e implementação dos adaptadores mock para:
- WhatsApp Business API
- Instagram Graph API
- Webhooks de callback
- Simulação de latência realística

---

## 🚀 Implementação

### [IMPLEMENTACAO-CAMADA2.md](implementacao/IMPLEMENTACAO-CAMADA2.md)
Documentação completa da Camada 2 (Upload de Arquivos e Integração Multiplataforma):
- Sistema de upload multipart
- Armazenamento em MinIO
- Mensagens com anexos
- Conectores multiplataforma
- Controle de status de mensagens

### [VERIFICACAO-FASE1.md](implementacao/VERIFICACAO-FASE1.md)
Checklist de verificação do MVP (Camada 1):
- Autenticação JWT
- Conversas privadas
- Envio de mensagens
- Histórico e estados

### [VERIFICACAO-FASE2.md](implementacao/VERIFICACAO-FASE2.md)
Checklist de verificação da Camada 2:
- Upload de arquivos
- Integração com plataformas
- Webhooks
- Testes E2E

### [SOLUCAO-RACE-CONDITION-WEBHOOK.md](implementacao/SOLUCAO-RACE-CONDITION-WEBHOOK.md)
Documentação da solução para race condition em webhooks:
- Problema identificado
- Workaround temporário (60-80s delay)
- Solução arquitetural final (WebhookTriggerService)
- Resultados de performance

### [CHECKLIST-ENTREGA.md](implementacao/CHECKLIST-ENTREGA.md)
Checklist final de entrega do projeto

---

## 🧪 Testes

### [TESTES.md](testes/TESTES.md)
Estratégia completa de testes:
- Testes unitários
- Testes de integração
- Testes E2E
- Cobertura de código

### [GUIA-POSTMAN.md](testes/GUIA-POSTMAN.md)
Guia completo para testar a API com Postman:
- Configuração do ambiente
- Testes gRPC
- Testes REST
- Collection de exemplos

### [EXEMPLOS-POSTMAN.md](testes/EXEMPLOS-POSTMAN.md)
Exemplos práticos de requests Postman:
- Criar conversa
- Enviar mensagem
- Upload de arquivo
- Consultar status

### [GUIA-TESTE-UPLOAD-ARQUIVO.md](testes/GUIA-TESTE-UPLOAD-ARQUIVO.md)
Guia específico para testes de upload de arquivos:
- Testes manuais
- Testes automatizados
- Validações esperadas

---

## 🗂️ Organização

```
docs/
├── INDICE.md                              # Este arquivo
├── RELATORIO-IMPLEMENTACAO-SEMANAS-7-8.md # Relatório consolidado Semanas 7-8
├── RELATORIO-MONITORAMENTO.md             # Relatório técnico de observabilidade
├── RELATORIO-TESTES-27-11-2025.md         # Relatório de testes de integração
├── arquitetura/                           # Documentação arquitetural
│   ├── ARQUITETURA.md
│   └── DESIGN-MOCKS.md
├── implementacao/                         # Status e progresso
│   ├── IMPLEMENTACAO-CAMADA2.md
│   ├── VERIFICACAO-FASE1.md
│   ├── VERIFICACAO-FASE2.md
│   ├── SOLUCAO-RACE-CONDITION-WEBHOOK.md
│   └── CHECKLIST-ENTREGA.md
├── observabilidade/                       # Monitoramento e métricas
│   ├── GUIA-MONITORAMENTO.md
│   └── grafana-dashboard-basic.json
└── testes/                                # Guias de teste
    ├── TESTES.md
    ├── GUIA-POSTMAN.md
    ├── EXEMPLOS-POSTMAN.md
    ├── GUIA-TESTE-UPLOAD-ARQUIVO.md
    └── LIMITACOES-TESTES-CARGA.md
```

---

## 🔗 Links Rápidos

- [README Principal](../README.md) - Início do projeto
- [Scripts de Teste](../scripts/test/) - Testes automatizados
- [Especificações](../specs/) - Contratos e especificações técnicas
- [Ferramentas](../tools/) - Postman collections, grpcurl

---

**Última atualização**: 27 de Novembro de 2025
