# Modelo de dados e integridade

## Agregados e invariantes

- **Book:** ISBN-13 único, preço positivo, ativo/inativo, uma editora e autores/categorias em N:N.
- **Inventory:** saldo não negativo; cada alteração produz `InventoryMovement` com motivo, ator e referência.
- **Order:** snapshots de itens/endereço/totais; status só transita pela máquina de estados.
- **PaymentAttempt:** no máximo uma tentativa aprovada por pedido e uma em processamento; chave idempotente única.
- **ReturnRequest:** uma solicitação aberta por pedido; retorno integral; prazos parametrizados.
- **OutboxMessage:** estado pendente/processando/concluída/falha, tentativa e próximo processamento.

## Índices mínimos

ISBN único; e-mail de usuário único; índices de busca para título, autor, editora; pedidos por usuário/status/data; pagamentos por pedido/status; outbox por estado/próxima execução; movimentações por livro/data.

## Retenção

Pedidos, pagamentos, devoluções e estoque: 5 anos propostos. Backups: 7 dias. A anonimização é propagada aos backups ao rotacionarem. Prazos são parametrizáveis e dependem de validação jurídica para produção.
