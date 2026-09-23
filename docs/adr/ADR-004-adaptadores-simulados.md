# ADR-004 - Integrações simuladas atrás de interfaces

**Status:** Aceito

## Decisão

Pagamento, frete, e-mail e armazenamento de arquivo são interfaces de negócio com adaptadores simulados no MVP. O gateway simula cartões e PIX configuráveis; frete usa faixas de CEP; e-mail grava/captura mensagens de teste; capas ficam em volume Docker.

## Consequências

O MVP é demonstrável offline e adaptadores reais futuros não alteram serviços de domínio. O contrato de cada porta deve ser testado.

**Rastreabilidade:** RF-005, RF-007..008, RF-012, RN-014, RNF-005; TASK-010, 013, 015, 022.
