# ADR-005 - Status de pedido e pagamento independentes

**Status:** Aceito

## Decisão

Pedido representa aquisição/entrega; cada tentativa de pagamento possui ciclo financeiro próprio. Cancelamento do pedido libera estoque sem aguardar estorno. Pagamento tardio abre estorno, sem reativar pedido.

## Consequências

Evita duas fontes de verdade para reembolso, suporta tentativas de cartão e exige transições validadas, histórico e idempotência de webhook.

**Rastreabilidade:** RF-008..011, RN-004..012; TASK-014..021.
