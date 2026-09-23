# ADR-003 - Outbox e jobs persistentes no PostgreSQL

**Status:** Aceito

## Decisão

Persistir tarefas assíncronas em tabela outbox e processá-las por `@Scheduled`. A mensagem contém tipo, payload, estado, tentativas e próxima execução. Um worker obtém o item de modo seguro e registra sucesso ou falha.

## Alternativas

- Eventos apenas em memória: descartado por perda após reinício.
- RabbitMQ/Kafka: adiado pela complexidade incompatível com o MVP.

## Consequências

Expiração de reserva, e-mails, estornos e prazo de devolução sobreviverão a reinícios. Processadores devem ser idempotentes e observáveis.

**Rastreabilidade:** RF-008, RF-011..012, RF-018, RN-004..007, RNF-007; TASK-003, 016, 021..023.
